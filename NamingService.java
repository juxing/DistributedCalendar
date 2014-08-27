import java.rmi.AlreadyBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;

public interface NamingService extends Remote {
    public NamingService[] getNexts() throws RemoteException;
    public NamingService[] getPrevs() throws RemoteException;
    public int getId() throws RemoteException;
    public void joinProcess(NamingService ns) throws RemoteException;
    public CalendarManager createCalendarManager(int id, int type, CalendarManager primary) throws RemoteException;
    public CalendarManager replicateCalendarManager(int id, CalendarManager primary, HashMap<String, CalendarObjectImpl> userCalObjs, 
    												HashMap<String, ArrayList<String>> groupEvents)throws RemoteException, AlreadyBoundException, ParseException;
    public ArrayList<CalendarManagerImpl> list() throws RemoteException;
}
