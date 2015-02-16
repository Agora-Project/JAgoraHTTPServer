
import java.util.HashSet;
import java.util.Set;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;


/**
 *
 * @author angle
 */
public class JAgoraServerApplicationConfig implements ServerApplicationConfig{

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        Set<ServerEndpointConfig> s = new HashSet<>();
        //s.add(ServerEndpointConfig.Builder.create(JAgoraWebSocketServlet.class, "/Websocket/Agora").build());
        return s;
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        Set<Class<?>> s = new HashSet<>();
        s.add(JAgoraWebSocketServer.class);
        return s;
    }
    
}
