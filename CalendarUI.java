//CalendarUI.java

import java.rmi.*;

public interface CalendarUI extends Remote {
    public void remind(Event e) throws RemoteException;
    public boolean isRunning() throws RemoteException;
}
