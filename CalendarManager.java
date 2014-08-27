import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;

public interface CalendarManager extends Remote {
    public CalendarObject createCalendar(String userName, CalendarUI ui) throws RemoteException, MalformedURLException, AlreadyBoundException, ParseException;
    public HashMap<String, CalendarObjectImpl> list() throws RemoteException;
    public void logOut(String user) throws RemoteException, IOException, NotBoundException;
    //public CalendarObject connectCalendar(String userName) throws RemoteException;
    public void setCalObjs(HashMap<String, CalendarObjectImpl> userCalObjs) throws RemoteException, AlreadyBoundException, ParseException;
    public void setGroupEvents(HashMap<String, ArrayList<String>> groupEvents) throws RemoteException;
    public void isAlive() throws RemoteException;
    public int getId() throws RemoteException;
    public int getType() throws RemoteException;
    public void addGroupEvent(String event, ArrayList<String> users) throws RemoteException;
    public void removeGroup(Event e, String owner) throws RemoteException;
}
