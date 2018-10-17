package core;

import metafile.MetaFile;
import tracker.TrackerClient;
import tracker.TrackerRequest;
import tracker.TrackerResponse;
import tracker.TrackerTask;
import utils.Datafile;
import utils.ExecuteShellCommand;
import utils.Logger;
import utils.MessageSender;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;


import java.io.File;

import org.apache.commons.io.FileUtils;

/**
 * Executable for the client.
 */
public class Leecher {
	
	public final static int PORT = 7000;
	public final static String FILE_NAME =  "videoJohn2.mp4";
	
	public final static String FILE_TO_SHARE = "./data/"+ FILE_NAME;
	public final static String TORRENT_PATH = "./data/" + FILE_NAME +".torrent";
	public final static String DOWNLOAD_DIRECTORY = "./download";
	

    private static final int NUM_THREADS = 8;
    private static final int BACKLOG = 10;
    private static final String CMD_USAGE = "NORMAL: java Client name port metafile directory\n" +
            "SHARING: java Client name port file trackerIP trackerPort";
    private static final int UNCHOKE_INTERVAL = 5;

    public static void main(String[] args) throws IOException {

    	String id = args[0];
    	String numClientes = args[1]; //Esto es solo para el archivo de log
    	String archivo = FILE_NAME;
    	
        Logger logger = new Logger(id);
        int port = PORT;

        boolean registerFile;
        MetaFile metaFile;
        String directory;
        registerFile = false;
        metaFile = MetaFile.parseMetafile(TORRENT_PATH);
        directory = DOWNLOAD_DIRECTORY;


        boolean createEmptyFile = !registerFile;        // if not registering new file
        Datafile datafile = new Datafile(
                createEmptyFile,
                metaFile.getInfo().getFilename(),
                directory,
                metaFile.getInfo().getFileLength(),
                metaFile.getInfo().getPieceLength());

        ConcurrentMap<Peer, Connection> connections = new ConcurrentHashMap<>();
        ConcurrentHashMap<Peer, Float> unchokedPeers = new ConcurrentHashMap<>();

        // probably shouldn't be local host if running on zoo or something
        InetSocketAddress client = new InetSocketAddress(InetAddress.getLocalHost(), port);
        TrackerClient trackerClient = new TrackerClient(client, metaFile.getAnnounce(), datafile);

        TrackerResponse initResponse = getInitialTrackerResponse(trackerClient, registerFile, connections, trackerClient, logger);

        //iptraf
        String command = "sudo iptraf-ng -d ens160 -B -L /home/isis/s5g4/logstorrent/prueba-"+numClientes+"-clientes-"+archivo+".log";
		ExecuteShellCommand.executeCommand(command, false);
		
		File log = new File("./logs/torrentlog-"+numClientes+"-clientes-"+archivo+".txt");
		PrintWriter pw = new PrintWriter(log);
		Calendar calendar = new GregorianCalendar();		
		pw.println("==================================Log Cliente==================================");
		pw.println(calendar.getTime().toString());
		pw.println("Nombre del archivo: "+FILE_NAME);
		pw.close();
        
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUM_THREADS);
        executor.scheduleAtFixedRate(new Unchoker(connections, datafile, unchokedPeers, logger), 0, UNCHOKE_INTERVAL, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(new TrackerTask(trackerClient, datafile.getFilename(), connections, executor, logger),
                0, Math.max(initResponse.getInterval() * 1000 / 2, 1000), TimeUnit.MILLISECONDS);
        new Thread(new Welcomer(port, BACKLOG, connections, logger, datafile)).start();
        new Thread(new Responder(connections, unchokedPeers, datafile, executor, logger, numClientes, archivo)).start();
    }

    // Get an initial peer list from the tracker and attempt to initiate connections with all peers.
    private static TrackerResponse getInitialTrackerResponse(TrackerClient trackerClient,
                                                             boolean registerFile,
                                                             ConcurrentMap<Peer, Connection> connections,
                                                             TrackerClient client,
                                                             Logger logger) {
        TrackerResponse response = null;
        try {
            if (registerFile) {
                response = trackerClient.update(TrackerRequest.Event.COMPLETED);
            } else {
                response = trackerClient.update(TrackerRequest.Event.STARTED);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Add new peers
        Set<Peer> peers = response.getPeers();
        for (Peer peer : peers) {
            if (!isPeerEqualToMe(peer, client)) {
                logger.log("Initializing connection to " + peer);
                connect(peer, trackerClient, connections, logger);
            }
        }
        return response;
    }

    // check if peer's IP and port equal this client's IP and port
    private static boolean isPeerEqualToMe(Peer peer, TrackerClient trackerClient) {
        return peer.getIp().equals(trackerClient.getClient().getAddress())
                && peer.getPort() == trackerClient.getClient().getPort();
    }

    private static void connect(Peer peer, TrackerClient trackerClient, ConcurrentMap<Peer, Connection> connections, Logger logger) {
        try {
            logger.log("connecting to " + peer.getIp() + " at port " + peer.getPort());

            Socket socket = new Socket(peer.getIp(), peer.getPort());

            Connection connection = Connection.getInitialState(socket);
            connections.put(peer, connection);
            MessageSender.sendHandshake(connection, peer, logger, trackerClient.getDatafile(), trackerClient);
            MessageSender.sendBitfield(connection, peer, logger, trackerClient.getDatafile().getBitfield());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
