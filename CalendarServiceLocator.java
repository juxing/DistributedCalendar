import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

public class CalendarServiceLocator {
	public static final int TRACKER_NUM = 5;		
	private static final int MAX = 1000;
	private static final int MIN = -1;
	private String[] trackers = null;
	
	public CalendarServiceLocator(int[] ts) {
		trackers = new String[TRACKER_NUM];
		for(int i = 0; i < ts.length; i++) {
			trackers[i] = "rmi://compute-0-" + Integer.toString(ts[i]) + ".local:" + Integer.toString(NamingServiceImpl.NSPORT) + "/NamingService";
		}
	}
		
	public CalendarManager lookup(String userName) {
		//First look for existing cms.
		CalendarManager cm = null;
		if((cm = getCalendarManager(userName)) != null)
			return cm;
		
		//If no corresponding cm exists, create a new one.
		int prev = MAX;  //Just pick a very small and very big number.
		int next = MIN;
		int nameId = hashName(userName);
		NamingService ns = getEntryPoint();
		if(isAlive(ns)) {
			try {
				int startId = ns.getId();
				while(isAlive(ns)) {
					next = ns.getId();
					//We assume when get entry point, we try server in order, that is, from smallest id to biggest id, once
					//has one valid server, we pick that one as entry point.					
					if((next == nameId) || ((nameId > prev) && (nameId < next)) || !hasNext(ns, startId)) {
						cm = ns.createCalendarManager(nameId, CalendarManagerImpl.PRIMARY, null);  //If donot find cm before, we must create a primary new cm.
						return cm;
					}
					prev = next;
					NamingService[] nexts = ns.getNexts();
					ns = nexts[0];
				}
				return null;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				return null;
			}
		}
		else
			return null;
	}
	
	//Only used for looking for primary cm.
	public CalendarManager getCalendarManager(String name) {
		int nameId = hashName(name);
		CalendarManager cm = null;
		NamingService ns = getEntryPoint();
		if(isAlive(ns)) {
			try {
				int startId = ns.getId();
				while(isAlive(ns)) {
					ArrayList<CalendarManagerImpl> cms = ns.list();
					if(cms != null) {
						for(int i = 0; i < cms.size(); i++) {
							CalendarManager rcm = (CalendarManager)cms.get(i);
							if((rcm.getId() == nameId) && (rcm.getType() == CalendarManagerImpl.PRIMARY))
								return rcm;
						}
					}
					if(!hasNext(ns, startId))
						return null;
					NamingService[] nexts = ns.getNexts();
					ns = nexts[0];
				}
				return null;
			} catch(RemoteException e) {
				return null;
			}
		}
		else
			return null;
	}
	
	public CalendarManager getCalendarManagerById(int nameId) {
		CalendarManager cm = null;
		NamingService ns = getEntryPoint();
		if(isAlive(ns)) {
			try {
				int startId = ns.getId();
				while(isAlive(ns)) {
					ArrayList<CalendarManagerImpl> cms = ns.list();
					if(cms != null) {
						for(int i = 0; i < cms.size(); i++) {
							CalendarManager rcm = (CalendarManager)cms.get(i);
							if((rcm.getId() == nameId) && (rcm.getType() == CalendarManagerImpl.PRIMARY))
								return rcm;
						}
					}
					if(!hasNext(ns, startId))
						return null;
					NamingService[] nexts = ns.getNexts();
					ns = nexts[0];
				}
				return null;
			} catch(RemoteException e) {
				return null;
			}
		}
		else
			return null;
	}
	
	public static int hashName(String userName) {
		return (Math.abs(userName.hashCode()) % 6) + 1;
	}
	
	private NamingService getEntryPoint() {
		NamingService ns = null;
		for(int i = 0; i < trackers.length; i++) {
			System.setSecurityManager(new RMISecurityManager());
	        try {
				ns = (NamingService)Naming.lookup(trackers[i]);
				break;
			} catch (MalformedURLException | RemoteException
					| NotBoundException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		}
		
		return ns;
	}
	
	private boolean hasNext(NamingService ns, int nameId) {
		if(ns != null) {
			try {
				NamingService[] nexts = ns.getNexts();
				NamingService next = nexts[0];
				if(next != null) {
					int id = next.getId();
					if(id == nameId)
						return false;
					else
						return true;
				}
				else
					return false;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				return false;
			}
		}
		else
			return false;
	}
	
	private boolean isAlive(NamingService ns) {
		if(ns != null) {
			try {
				int id = ns.getId();
				return true;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				return false;
			}
		}
		else
			return false;
	}
	
	public HashMap<String, CalendarObjectImpl> globalList() {
		HashMap<String, CalendarObjectImpl> res = new HashMap<String, CalendarObjectImpl>();
		CalendarManager cm = null;
		NamingService ns = getEntryPoint();
		if(isAlive(ns)) {
			try {
				int startId = ns.getId();
				while(isAlive(ns)) {
					ArrayList<CalendarManagerImpl> cms = ns.list();
					if(cms != null) {
						for(int i = 0; i < cms.size(); i++) {
							CalendarManager rcm = (CalendarManager)cms.get(i);
							
							if(rcm.getType() == CalendarManagerImpl.PRIMARY) {  //Fix a bug, we only need Primary cm's cos, if without this condition,
								//we include backup cms, then we will never get any user online, the UI is always null.
								HashMap<String, CalendarObjectImpl> tmp = rcm.list();
								res.putAll(tmp);
							}							
						}
					}
					if(!hasNext(ns, startId))
						return res;
					NamingService[] nexts = ns.getNexts();
					ns = nexts[0];
				}
				return null;
			} catch(RemoteException e) {
				return null;
			}
		}
		else
			return null;
	}
		
}
