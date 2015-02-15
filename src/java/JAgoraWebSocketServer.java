


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.agora.lib.IJAgoraLib;
import org.agora.lib.JAgoraComms;
import org.agora.logging.Log;
import org.agora.server.DatabaseConnection;
import org.agora.server.JAgoraServer;
import org.agora.server.Options;
import org.agora.server.UserSession;
import org.agora.server.Util;
import org.agora.server.queries.*;
import org.bson.BSONEncoder;
import org.bson.BasicBSONEncoder;
import org.bson.BasicBSONObject;

/**
 *
 * @author angle
 */
@ServerEndpoint(value = "/Websocket/Agora")
public class JAgoraWebSocketServer implements JAgoraServer{
    
    private Session session;
    
    protected Random rand;
    
    protected Map<Integer, QueryResponder> responders;
    
    protected static ConcurrentMap<Integer, UserSession> sessions;
    
    public JAgoraWebSocketServer() {
        
    }
    
    @OnOpen
    public void start(Session session) {
        rand = new Random();
        this.session = session;
        
        if (sessions == null) {
            sessions = new ConcurrentHashMap<>();
        }
        
        initialiseResponders();
        
        readConfigurationFiles();
    }

    @OnClose
    public void end() {
 	
    }
 	
    @OnMessage
    public boolean incoming(byte[] message) {
 	BasicBSONObject request = JAgoraComms.readBSONObjectFromStream(
                new DataInputStream(new ByteArrayInputStream(message)));
        if (request == null)
            return false;
    
        BasicBSONObject response = processBSONRequest(request);
        if (response == null)
            return false;
        
        BSONEncoder benc = new BasicBSONEncoder();
        byte[] b = benc.encode(response);
        session.getAsyncRemote().sendObject(ByteBuffer.wrap(b));
        return true;
    }
    
    public BasicBSONObject processBSONRequest(BasicBSONObject request) {
        int requestType = (Integer) request.get(IJAgoraLib.ACTION_FIELD);
        QueryResponder r = getResponder(requestType);
        if (r == null) {
           BasicBSONObject response = new BasicBSONObject();
           response.put(IJAgoraLib.RESPONSE_FIELD, IJAgoraLib.SERVER_FAIL);
           response.put(IJAgoraLib.REASON_FIELD, "Cannot handle this request.");
           return response;
        }
        return r.respond(request, this);
  }
 	
    @OnError
    public void onError(Throwable t) throws Throwable {
    }

    @Override
    public QueryResponder getResponder(int operation) {
        if (!responders.containsKey(operation))
            return null;
        return responders.get(operation);
    }

    @Override
    public DatabaseConnection createDatabaseConnection() {
        DatabaseConnection dbc = new DatabaseConnection(Options.DB_URL,
                                                        Options.DB_USER,
                                                        Options.DB_PASS);
        boolean connected = dbc.open();
        if(!connected) {
            Log.error("[JAgoraServer] Could not initiate connection to database.");
            return null;
        }

        return dbc;
    }

    @Override
    public UserSession userLogin(String user, int userID, int userType) {
        byte[] sessBytes = new byte[Options.SESSION_BYTE_LENGTH];
        rand.nextBytes(sessBytes);
        String sessionID = Util.bytesToHex(sessBytes);
        UserSession session = new UserSession(user, userID, sessionID, userType);
        sessions.put(userID, session);
        return session;
    }

    @Override
    public UserSession getSession(int userID) {
        if (!sessions.containsKey(userID))
            return null;
        return sessions.get(userID);
    }

    @Override
    public boolean verifySession(int userID, String sessionID) {
        UserSession us = getSession(userID);
        if (us == null)
            return false;

        return (us.getSessionID().equals(sessionID) && us.hasPostingPrivilege());
    }

    @Override
    public boolean verifySession(BasicBSONObject query) {
        int userID = query.getInt(IJAgoraLib.USER_ID_FIELD);
        String sessionID = query.getString(IJAgoraLib.SESSION_ID_FIELD);

        UserSession us = getSession(userID);
        if (us == null)
            return false;

        return (us.getSessionID().equals(sessionID) && us.hasPostingPrivilege());
    }

    @Override
    public boolean logoutUser(int userID) {
        return sessions.remove(userID) != null;
    }

    @Override
    public BlockingQueue<Socket> getRequestQueue() {
        throw new UnsupportedOperationException("Operation will never be supported, use JAgoraSocketServer instead."); 
    }
    
    protected void readConfigurationFiles() {
      Options.readDBConfFromFile();
      Options.readAgoraConfFromFile();
    }
    
    protected void initialiseResponders() {
    responders = new HashMap<Integer, QueryResponder>();
    responders.put(IJAgoraLib.LOGIN_ACTION, new LoginResponder());
    responders.put(IJAgoraLib.LOGOUT_ACTION, new LogoutResponder());
    responders.put(IJAgoraLib.QUERY_BY_THREAD_ID_ACTION, new ThreadByIDResponder());
    responders.put(IJAgoraLib.ADD_ARGUMENT_ACTION, new AddArgumentResponder());
    responders.put(IJAgoraLib.ADD_ATTACK_ACTION, new AddAttackResponder());
    responders.put(IJAgoraLib.ADD_ARGUMENT_VOTE_ACTION, new AddArgumentVoteResponder());
    responders.put(IJAgoraLib.ADD_ATTACK_VOTE_ACTION, new AddAttackVoteResponder());
    responders.put(IJAgoraLib.REGISTER_ACTION, new RegisterResponder());
    responders.put(IJAgoraLib.QUERY_THREAD_LIST_ACTION, new ThreadListResponder());
    responders.put(IJAgoraLib.EDIT_ARGUMENT_ACTION, new EditArgumentResponder());
    responders.put(IJAgoraLib.QUERY_BY_ARGUMENT_ID_ACTION, new QueryArgumentByIDResponder());
  }
    
}
