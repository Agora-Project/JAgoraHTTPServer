
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.agora.lib.IJAgoraLib;
import org.agora.lib.JAgoraComms;
import org.agora.logging.ConsoleLog;
import org.agora.logging.Log;
import org.agora.server.DatabaseConnection;
import org.agora.server.JAgoraServer;
import org.agora.server.Options;
import org.agora.server.UserSession;
import org.agora.server.Util;
import org.agora.server.queries.*;
import org.bson.BasicBSONObject;

/**
 *
 * @author angle
 */


public class JAgoraHttpServer extends HttpServlet implements JAgoraServer {
    
    protected Random rand;
    
    protected Map<Integer, QueryResponder> responders;
    
    protected static ConcurrentMap<Integer, UserSession> sessions;
    
    public JAgoraHttpServer() {}
    
    @Override
    public void init() {
        rand = new Random();
        Log.addLog(new ConsoleLog());
        
        if (sessions == null) {
            sessions = new ConcurrentHashMap<>();
        }
        
        initialiseResponders();
        
        try {
            readConfigurationFiles();
        } catch (MalformedURLException e) {
            log("[JAgoraHttpServer] could not read conf files: " + e.getMessage());
            Log.error("[JAgoraHttpServer] could not read conf files: " + e.getMessage());
        }
    }
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        ServletInputStream inputStream = request.getInputStream();
        BasicBSONObject bsonRequest = JAgoraComms.readBSONObjectFromStream(inputStream);
        int requestType = (Integer)bsonRequest.get(IJAgoraLib.ACTION_FIELD);
        QueryResponder r = getResponder(requestType);
        BasicBSONObject bsonResponse = null;
        if (r == null) {
            bsonResponse = new BasicBSONObject();
            bsonResponse.put(IJAgoraLib.RESPONSE_FIELD, IJAgoraLib.SERVER_FAIL);
            bsonResponse.put(IJAgoraLib.REASON_FIELD, "Cannot handle this request.");
        } else {
            bsonResponse = r.respond(bsonRequest, this);
        }
        response.setContentType("application/bin");
        ServletOutputStream outputStream = response.getOutputStream();
        JAgoraComms.writeBSONObjectToStream(outputStream, bsonResponse);
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
    
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
            Log.error("[JAgoraHttpServer] Could not initiate connection to database.");
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    protected void readConfigurationFiles() throws MalformedURLException {
      try { 
        Options.readDBConfFromStream(getServletContext().getResourceAsStream("/WEB-INF/" + Options.DB_FILE));
        Options.readAgoraConfFromStream(getServletContext().getResourceAsStream("/WEB-INF/" + Options.CONF_FILE));
      } catch (Exception e) {
          log("[JAgoraHttpServer] could not read conf files from " 
                  + getServletContext().getResource("/WEB-INF/" + Options.DB_FILE) + ": " + e.getMessage());
          Log.error("[JAgoraHttpServer] could not read conf files from " 
                  + getServletContext().getResource("/WEB-INF/" + Options.DB_FILE) + ": " + e.getMessage());
          throw e;
      }
    }
    
    protected void initialiseResponders() {
    responders = new HashMap<>();
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
