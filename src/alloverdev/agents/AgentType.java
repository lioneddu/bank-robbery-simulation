package alloverdev.agents;

public enum AgentType {
    /*
    SUP=Supervisor;
    THF=Thief;
    CLN=Client;
    EMP=Employee;
    GRD=Guard.
     */
    SUP("Supervisor"), THF("Thief"), CLN("Client"), EMP("Employee"), GRD("Guard"), GRV("Grave");

    public final String className;

    private AgentType(String className) {
        this.className = className;
    }
}
