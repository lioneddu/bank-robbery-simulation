package alloverdev.interfaces;

import alloverdev.agents.AgentType;
import java.awt.Image;
import alloverdev.broker.*;

public interface AgentsIF {

    AgentType getType();

    void subscribe(boolean b);

}
