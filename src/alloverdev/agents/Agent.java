package alloverdev.agents;

import alloverdev.broker.*;

public abstract class Agent implements Runnable {

    protected Connection conn;

    protected String SUP_1, SUP_2, SUP_3, SUP_4, SUP_6, SUP_7,
            CLN_1, CLN_2, CLN_3, CLN_4, CLN_5, CLN_7,
            EMP_1, EMP_2, EMP_3, EMP_4, EMP_5, EMP_6,
            THF_1, THF_2, THF_3, THF_5,
            GRD_1, GRD_2, GRD_3, GRD_5,
            ALARM,
            KILLME;

    //Constructor
    public Agent(Connection conn) {
        this.conn = conn;
        //Tutte le activity
        SUP_1 = conn.getProperty("SUP_1");
        SUP_2 = conn.getProperty("SUP_2");
        SUP_3 = conn.getProperty("SUP_3");
        SUP_4 = conn.getProperty("SUP_4");
        SUP_6 = conn.getProperty("SUP_6");
        SUP_7 = conn.getProperty("SUP_7");
        CLN_1 = conn.getProperty("CLN_1");
        CLN_2 = conn.getProperty("CLN_2");
        CLN_3 = conn.getProperty("CLN_3");
        CLN_4 = conn.getProperty("CLN_4");
        CLN_5 = conn.getProperty("CLN_5");
        CLN_7 = conn.getProperty("CLN_7");
        EMP_1 = conn.getProperty("EMP_1");
        EMP_2 = conn.getProperty("EMP_2");
        EMP_3 = conn.getProperty("EMP_3");
        EMP_4 = conn.getProperty("EMP_4");
        EMP_5 = conn.getProperty("EMP_5");
        EMP_6 = conn.getProperty("EMP_6");
        THF_1 = conn.getProperty("THF_1");
        THF_2 = conn.getProperty("THF_2");
        THF_3 = conn.getProperty("THF_3");
        THF_5 = conn.getProperty("THF_5");
        GRD_1 = conn.getProperty("GRD_1");
        GRD_2 = conn.getProperty("GRD_2");
        GRD_3 = conn.getProperty("GRD_3");
        GRD_5 = conn.getProperty("GRD_5");
        ALARM = conn.getProperty("ALARM");
        KILLME = conn.getProperty("KILLME");

        if (SUP_1 == null || SUP_2 == null || SUP_3 == null || SUP_4 == null || SUP_6 == null || SUP_7 == null
                || CLN_1 == null || CLN_2 == null || CLN_3 == null || CLN_4 == null || CLN_5 == null || CLN_7 == null
                || EMP_1 == null || EMP_2 == null || EMP_3 == null || EMP_4 == null || EMP_5 == null || EMP_6 == null
                || THF_1 == null || THF_2 == null || THF_3 == null || THF_5 == null
                || GRD_1 == null || GRD_2 == null || GRD_3 == null || GRD_5 == null
                || ALARM == null || KILLME == null) {

            System.err.println("Activity non definita. Hai aggiornato il project properties?");
        }
    }

    protected void checkStatus(AbstractMessage msg, boolean showxml) {
        XMLInterface status = msg.getStatus();
        String code = status.getProperty("code");

        // KO is output as an informational message so that it cannot be blocked
        if ("KO".equals(code)) {
            quitVM("... abbiamo dei problemi !! \n"
                    + "Message: " + status.getProperty("message") + "\n"
                    + "Exception: " + status.getProperty("exception") + "\n");
        }
    }

    /**
     * Metodo per verificare l'operato del broker e visualizzare le risposte del
     * server
     *
     * @param msg messaggio generato
     * @param b booleano per ricevere la rispsota dal server (true) o no (false)
     */
    public void showStatus(AbstractMessage msg, boolean b) {
        XMLInterface status = msg.getStatus();
        String code = status.getProperty("code");

        if (b) {
            System.out.println();
            System.out.println("XML Reply ricevuta dal Server:");
            System.out.println(msg.getXMLReply());
        }

        System.out.println();
        System.out.println("Lo Status della Reply per " + msg.getClass().getName()
                + " e': " + code);
        if ("KO".equals(code)) {
            System.out.println("... abbiamo dei problemi !!!");
            System.out.println("Message: " + status.getProperty("message"));
            System.out.println("Exception: " + status.getProperty("exception"));
            System.exit(0);
        }
        System.out.println();
    }

    private void quitVM(String message) {
        System.out.println();
        System.out.println("*****<alert>*****");
        System.out.println("Exiting VM");
        System.out.println(message);
        System.out.println("*****</alert>*****");
        System.exit(0);
    }

    public Connection getConn() {
        return conn;
    }

}
