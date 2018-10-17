package core;

import message.Bitfield;
import message.Piece;
import utils.Datafile;
import utils.ExecuteShellCommand;
import utils.Logger;
import utils.MessageSender;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.io.FileUtils;

/**
 * Handles this peer downloading from other peers.
 */
public class Downloader {

    private Logger logger;
    private String numClientes;
    private String archivo;
    
    private long initTime = 0;
    private long endTime = 0;
    
    private boolean received = false;

    public Downloader(Logger logger, String numClientes, String archivo) {
        this.logger = logger;
        this.archivo = archivo;
        this.numClientes = numClientes;
    }

    public void receiveChoke(Connection connection, Peer peer) {
        connection.getDownloadState().setChoked(true);
        logger.log("receive CHOKE from " + peer);
    }

    public void receiveUnchoke(Connection connection, Peer peer, Datafile datafile) {
        connection.getDownloadState().setChoked(false);
        logger.log("receive UNCHOKE from " + peer);
        requestFirstAvailPiece(connection, peer, datafile);   // request first available piece
    }

    public void receivePiece(Connection connection,
                             Peer peer,
                             ConcurrentMap<Peer, Connection> connections,
                             Datafile datafile,
                             Piece piece) {
        logger.log(String.format("receive PIECE for pieceIndex:%d from " + peer, piece.getPieceIndex()));
        datafile.getBitfield().setPieceToCompleted(piece.getPieceIndex());                  // 1. update bitfield
        datafile.writePiece(piece.getBlock(), piece.getPieceIndex());                       // 2. write piece
        connection.incrementBytesDownloaded(piece.getBlock().length);                       // 3. update bytes downloaded
        for (Map.Entry<Peer, Connection> peerConnection : connections.entrySet()) {         // 4. broadcast Have new piece to all peers
            if (!peerConnection.getValue().equals(connection)) {
                MessageSender.sendHave(peerConnection.getValue(), peer, logger, piece.getPieceIndex());
                logger.log("send HAVE to " + peerConnection.getKey());
            }
        }
        requestFirstAvailPiece(connection, peer, datafile);                                 // 5. request next piece
    }

    public void receiveBitfield(Connection connection, Peer peer, Bitfield bitfield, Datafile datafile) {
        logger.log("receive BITFIELD " + bitfield + " from " + peer);
        connection.setBitfield(bitfield);               // set peer's bitfield
        if (!datafile.isCompleted()) {
            MessageSender.sendInterested(connection, peer, logger);
        }
    }

    /**
     * Requests from peer the first missing piece that peer has.
     */
    private void requestFirstAvailPiece(Connection connection, Peer peer, Datafile datafile) {
        if (datafile.isCompleted()) {
        	
        	//LOG
        	this.endTime = System.currentTimeMillis();   
    		long time = endTime-initTime;
    		
    		try {
    		File log = new File("./logs/torrentlog-"+numClientes+"-clientes-"+archivo+".txt");
			PrintWriter pw = new PrintWriter(log);
			pw.println("Tamaño del archivo: "+datafile.getFileLength()+" bytes");
			pw.println("Tiempo de transferencia: "+time/1000.0);
			pw.println(isDownloadedFileComplete() ? "El archivo fue recibido exitosamente." : "El archivo no fue recibido correctamente.");
			pw.close();
    		}
    		catch(Exception e)
    		{e.printStackTrace();}
    		
    		ExecuteShellCommand.executeCommand("sudo killall -USR2 iptraf-ng", false);
    		
    		
            logger.log("datafile is complete! WOOOOOOOOOOOO!");
            return;
        }
        for (int i = 0; i < datafile.getNumPieces(); i++) {
            if (datafile.getBitfield().missingPiece(i) && connection.getBitfield().hasPiece(i)) {
            	
                MessageSender.sendRequest(connection, peer, logger, i, datafile.getPieceLength()); // request entire piece
                if(!received)
            	{
            		received = true;
            		this.initTime = System.currentTimeMillis();
            	}
                break;
            }
        }
    }

    public Logger getLogger() {
        return logger;
    }

    private void writeLog(String s, boolean finish) {
    	try {
    		FileWriter pw = new FileWriter("./data/log.csv", true);
    		pw.append(s);
    		if(finish) {
    			pw.append("\n");
    		}
    		else {
    			pw.append(",");
    		}
    		pw.flush();
    		pw.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }

    private static boolean isDownloadedFileComplete() {

    	try 
    	{
    		File file1 = new File(Leecher.DOWNLOAD_DIRECTORY+"/"+Leecher.FILE_NAME);
    		File file2 = new File(Leecher.FILE_TO_SHARE);
    		boolean isTwoEqual = FileUtils.contentEquals(file1, file2);
    		return isTwoEqual;
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    		return false;
    	}

    }
}
