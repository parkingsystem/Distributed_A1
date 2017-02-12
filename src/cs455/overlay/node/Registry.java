package cs455.overlay.node;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.stream.IntStream;

import cs455.overlay.commands.DeRegistrationCommand;
import cs455.overlay.commands.Link;
import cs455.overlay.commands.LinkWeightsCommand;
import cs455.overlay.commands.MessaginNodesListCommand;
import cs455.overlay.commands.Node;
import cs455.overlay.commands.RegistrationCommand;
import cs455.overlay.commands.ResponseCommand;
import cs455.overlay.commands.TaskInitiateCommand;

public class Registry implements Runnable {

	public static final String EXIT_COMMAND = "exit";

	public static int NODE_COUNTER = 0;
	public static String[] arr = { "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T" };

	HashMap<Integer, Node> registeredNodes = new HashMap<Integer, Node>();
	HashMap<Node, ArrayList<Node>> overLay;
	HashSet<Link> links = new HashSet<Link>();
	private ServerSocket serversocket;
	private String registryNodeName;
	private int messageRecevingPort;
	private int Cr = 4; // TODO: change this to four later

	private Registry() {
	}

	public ServerSocket getServersocket() {
		return serversocket;
	}

	public static void main(String[] args) throws Exception {
		if(args.length < 1) {
			System.out.println("Error: Please pass registry port number");
			System.exit(0);
		}
		
		int port = 0;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Error: Please provide numneric argument.");
			System.exit(0);
		}
		
		Registry registryNode = new Registry();
		registryNode.intializeRegistryNode(port);

		Thread t = new Thread(registryNode);
		t.start();

		boolean continueOperations = true;
		while (continueOperations) {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String exitStr = br.readLine();
			System.out.println("Received command is:" + exitStr);

			if (EXIT_COMMAND.equalsIgnoreCase(exitStr)) {
				System.out.println("Exiting.");
				continueOperations = false;
			} else if ("setup-overlay".equalsIgnoreCase(exitStr)) {
				registryNode.setupOverlay();
			} else if ("assign-weights".equalsIgnoreCase(exitStr)) {
				registryNode.assignWeights();
			} else if("task-initiate".equalsIgnoreCase(exitStr)) {
				registryNode.taskInitiate();
			} else if("pull-traffic-summary".equalsIgnoreCase(exitStr) ) {
				registryNode.trafficSummary();
			}
		}
		System.out.println("Bye.");
	}

	private void trafficSummary() {
		
	}

	private void taskInitiate() throws Exception {
		TaskInitiateCommand cmd = new TaskInitiateCommand();
		cmd.rounds = 10;
		
		byte[] data = cmd.unpack();
		for (Node key : overLay.keySet()) {
			System.out.println("Sending Task Initiate to:" + key);
			createSocketFAndSendOutgoingMessages(key.ipAddress, key.port, data);
		}
	}

	private void assignWeights() throws Exception {
		if(overLay == null || overLay.keySet().isEmpty()) {
			System.out.println("Overlay is empty");
		}
		links = new HashSet<>();
		System.out.println((registeredNodes.size() * Cr) + 10);
		IntStream ints = new Random().ints((registeredNodes.size() * Cr) + 10, 0, 10);
		int[] array = ints.toArray();
		int arraIndex =0;
		
		for (Node key : overLay.keySet()) {
			ArrayList<Node> conectedWith = overLay.get(key);
			for (Node eachConnection : conectedWith) {
				Link link = new Link(key, eachConnection, array[arraIndex]);
				Link inverseLink = new Link(eachConnection, key, array[arraIndex]);
				if(!links.contains(link)) {
					links.add(link);
					links.add(inverseLink);
					arraIndex++;
				}
			}
		}
		LinkWeightsCommand cmd = new LinkWeightsCommand(links);
		byte[] data = cmd.unpack();
		for (Node key : overLay.keySet()) {
			System.out.println("Sending link weights to:" + key);
			createSocketFAndSendOutgoingMessages(key.ipAddress, key.port, data);
		}
	}

	public ResponseCommand addMessaginNode(String IP, int port) {

		System.out.println("Received Registration request...");
		System.out.println("Node Counter :::" + NODE_COUNTER);
		ResponseCommand cmd = null;
		if (registeredNodes.containsKey(port)) {
			System.out.println("Already regisred");
			cmd = new ResponseCommand("REGISTER_RESPONSE", "FAILURE", "Node Already regisred");
		} else {
			Node n = new Node();
			n.ipAddress = IP;
			n.port = port;
			n.node_name = arr[NODE_COUNTER];
			registeredNodes.put(port, n);
			System.out.println("Messaging node added: " + n);
			NODE_COUNTER += 1;
			cmd = new ResponseCommand("REGISTER_RESPONSE", "SUCCESS", "Node regisred");
		}
		System.out.println("Total Registerd Nodes " + registeredNodes.size());
		return cmd;
	}

	public ResponseCommand RemoveMessaginNode(String IP, int port) {

		System.out.println("Received De-Registration request..." + IP + ":" + port);
		ResponseCommand cmd = null;
		if (registeredNodes.containsKey(port)) {
			registeredNodes.remove(port);
			cmd = new ResponseCommand("DEREGISTER_RESPONSE", "SUCCESS", "Node de-regisred");
		} else {
			cmd = new ResponseCommand("DEREGISTER_RESPONSE", "FAILURE", "Node not registered.");
		}

		System.out.println("Total Registerd Nodes " + registeredNodes.size());
		return cmd;
	}

	private void setupOverlay() throws IOException {
		System.out.println("Setup Overlay");
		setupCollectionProcessforNodeAsPeerMesagingNode();
	}

	public void setupCollectionProcessforNodeAsPeerMesagingNode() throws IOException {
		SetupOverLayStrategy strategy = new SetupOverLayStrategy();
		ArrayList<Node> nodes = new ArrayList<>(registeredNodes.values());
		overLay = strategy.SelectNodeForPeerConection(nodes, Cr);
		sendMessageToPeerNodeForconnectionwithOtherPeerNode(overLay);
	}

	public void sendMessageToPeerNodeForconnectionwithOtherPeerNode(HashMap<Node, ArrayList<Node>> peerList)
	        throws IOException {
		for (Node node : peerList.keySet()) {
			ArrayList<Node> peerNodelist = peerList.get(node);
			createSocketForOvelaySetup(peerNodelist, node);
		}
	}

	public void createSocketForOvelaySetup(ArrayList<Node> peerNodelist, Node n) throws IOException {

		Socket sc = null;
		System.out.println("Sending OverLay details to each node.");
		try {
			sc = new Socket(n.ipAddress, n.port);
			MessaginNodesListCommand cmd = new MessaginNodesListCommand(peerNodelist);
			byte[] data = cmd.unpack();
			new TCPSender().sendData(sc, data);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			sc.close();
		}
	}

	public void createSocketFAndSendOutgoingMessages(String ipAddress, int port, byte[] data) throws IOException {

		Socket sc = null;
		System.out.println("Ready to send message to message node");
		try {
			sc = new Socket(ipAddress, port);
			new TCPSender().sendData(sc, data);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			sc.close();
		}
	}

	private void intializeRegistryNode(int port) throws IOException {
		System.out.println("initializing Registry....");
		this.registryNodeName = "Registry-Node";
		this.serversocket = new ServerSocket(port);
		this.messageRecevingPort = this.serversocket.getLocalPort();
		System.out.println("Registry Node Name : " + this.registryNodeName + " Listenning on :" + this.messageRecevingPort);
	}

	@Override
	public void run() {
		while (true) {
			DataInputStream din = null;
			Socket socket = null;

			try {
				socket = serversocket.accept();

				System.out.println(">>> Received Request:");
				din = new DataInputStream(socket.getInputStream());

				int request_Typelength = din.readInt();
				byte[] request_Type = new byte[request_Typelength];
				din.readFully(request_Type);
				String str_request_type = new String(request_Type);
				System.out.println("RequestType: " + str_request_type);

				if (str_request_type != null && str_request_type.equalsIgnoreCase("REGISTER_REQUEST")) {
					RegistrationCommand cmd = new RegistrationCommand();
					cmd.pack(din);
					ResponseCommand responseCmd = addMessaginNode(cmd.ipAddress, cmd.fromPort);
					createSocketFAndSendOutgoingMessages(cmd.ipAddress, cmd.fromPort, responseCmd.unpack());
				}
				if (str_request_type != null && str_request_type.equalsIgnoreCase("DEREGISTER_REQUEST")) {
					DeRegistrationCommand cmd = new DeRegistrationCommand();
					cmd.pack(din);
					ResponseCommand responseCmd = RemoveMessaginNode(cmd.ipAddress, cmd.port);
					createSocketFAndSendOutgoingMessages(cmd.ipAddress, cmd.port, responseCmd.unpack());
				}
				din.close();
				socket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}