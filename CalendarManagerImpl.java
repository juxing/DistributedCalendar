import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CalendarManagerImpl extends UnicastRemoteObject implements CalendarManager {
    //public CalendarObjectImpl co = null;
    //Only register online user, so don't have to store? Or only restore username, but not object, because objects needs to re-generated.
    //String label all the user we have, and CalendarObjectImpl label all the user online, if it is null, we mean this user used to online, but now isn't, 
    //but we can find his calendar from a file.
    //Need store.
	public HashMap<String, CalendarObjectImpl> userCalObjs = null;  //Register of all CalendarObjectImpls
	//Need store.
	private HashMap<String, ArrayList<String>> groupEvents = null;  //Register of all group events. But group event name must be different because of hashmap, a bug.
	//private String hostName = null;  //Hostname of server on which CalendarManager is running.
	//private static final String backFile = "CalendarManager"; 
	
	//Synchronize objects, whenever read or write userCalObjs or groupEvents data, must first synchronize on these two objects.
	/*private Object userCalObjsLock = new Object();
	private Object groupEventsLock = new Object();*/
	
	private static final int REPLICATE_INTERVAL = 4000;
	public static final int PRIMARY = 0;
	public static final int BACKUP = 1;
	private int id = -1;
	private int type = -1;
	private CalendarManager primary = null;
	public CalendarManager replica = null;
	public NamingServiceImpl namingService = null;
	private boolean isRunning = false;  //Control thread.
		
    public CalendarManagerImpl(int newId, int newType, CalendarManager newPrimary, NamingServiceImpl ns) throws AlreadyBoundException, ClassNotFoundException, IOException, ParseException, NotBoundException {
        super();
        
        id = newId;
        type = newType;
        primary = newPrimary;
        namingService = ns;  //The local parent naming service.
        
        //If back up file exist, load data from this file.
        String cmName = getFileName(newId, newType);
        CalendarManagerData mgData = null;
        if((mgData = CalendarManagerData.load(cmName)) != null) {
        	userCalObjs = mgData.getUserCalObjs();  //At now, the userCalObjs only contains user names, all CalendarObjectImpls are null.
        	groupEvents = mgData.getGroupEvents();
        	
        	//Offline issue
        	Iterator<String> it = userCalObjs.keySet().iterator();
        	while(it.hasNext()) {
        		String name = it.next();
        		//Initialize CalendarObjectImpl objects without CalendarUIImpl objects, this means those users are not online yet, but we can still
        		//operate on their CalendarObjects.
        		userCalObjs.put(name, new CalendarObjectImpl(name, this, null));  //This new created calendar objects will read their files.
        	}
        	
        	System.out.println("Read Calendar Manager: " + id + "-" + type + " from " + cmName);
        }
        //Else initialize new user objects and group events.
        else {
	        userCalObjs = new HashMap<String, CalendarObjectImpl>();
	        groupEvents = new HashMap<String, ArrayList<String>>();
        }
        
        /*System.setSecurityManager(new RMISecurityManager());
        LocateRegistry.createRegistry(8888);
        InetAddress inet = InetAddress.getLocalHost();
        hostName = inet.getCanonicalHostName();
        String calManAddr = "rmi://" + hostName + ":8888/CalendarManager";
        Naming.bind(calManAddr, this);*/
        
        /*debugUserCalObjs();
        debugGroupEvents();*/
        //System.out.println("Server started.\n");
	    System.out.println("Calendar Manager id: " + id + ", type: " + type + " is created.");
    }
    
    @Override
    public int getId() {
    	return id;
    }
    
    @Override
    public int getType() {
    	return type;
    }
    
    /*public Object getUserCalObjsLock() {
    	return userCalObjsLock;
    }
    
    public Object getGroupEventsLock() {
    	return groupEventsLock;
    }*/

    //When ui is not null, this will always create primary calendar object.
    public CalendarObjectImpl createCalendar(String userName, CalendarUI ui) throws AlreadyBoundException, ParseException, RemoteException {
    	//At this moment, only allow one user login at a time, maybe improve later.
    	//If just pass CalendarObject to user instead of starting up an rmi server, this problem will be solved. Fix it later. Now just keep it.
    	//This user is already online.
    	
    	//Synchronize
    	//synchronized(userCalObjsLock) {
    		    	
    	//Offline issue.
        //if(userCalObjs.containsKey(userName) && (userCalObjs.get(userName) != null)) {
        if(userCalObjs.containsKey(userName) && (userCalObjs.get(userName) != null) && (userCalObjs.get(userName).hasUI())) {  //User is already online, can't login twice.
        	//String coAddr = "rmi://" + hostName + ":8888/" + userName + "CalendarObject";
        	//debugUserCalObjs(); 
        	return null;
        }
        else if(userCalObjs.containsKey(userName) && (userCalObjs.get(userName) != null) && (!userCalObjs.get(userName).hasUI())) {  //User is offline now.
        	userCalObjs.get(userName).setUI(ui);  //Combine CalendarUIImpl with corresponding CalendarObjectImpl. Start Notificator.
        	System.out.println("Set " + userName + " Calendar Object UI.");
        	//String coAddr = "rmi://" + hostName + ":8888/" + userName + "CalendarObject";
            //Naming.bind(coAddr, userCalObjs.get(userName));
            //debugUserCalObjs(); 
            //return coAddr;
        	return userCalObjs.get(userName);
        }
        else if(!userCalObjs.containsKey(userName)) {  //User doesn't exit, create a new user.       	
        	CalendarObjectImpl co = null;
			try {
				co = new CalendarObjectImpl(userName, this, ui);
			} catch (ClassNotFoundException | NotBoundException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	co.setUI(ui);  //Start notificator.
        	
        	/*String coAddr = "rmi://" + hostName + ":8888/" + userName + "CalendarObject";
	        Naming.bind(coAddr, co);*/
	        //If load from file, user name exist, but calobj is null, don't worry, hashmap will override the old one. Hope so!
	        userCalObjs.put(userName, co);
	        //debugUserCalObjs(); 
	        
	        //Create calendar object on replica calendar manager.
	        if((type == PRIMARY) && (replica != null)) {
	        	try {
					replica.createCalendar(userName, null);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
					System.out.println("Failed to replicate calendar object.");
				}
	        }
	        System.out.println("Create calendar object: " + userName);    
	        
    		try {
				saveData();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		
	        return co;
        }
        else
        	return null;
    	
    	//}  //End of sync.
        //Offline issue.
    	/*try {
			co = new CalendarObjectImpl(userName, this, ui);
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        String coAddr = "rmi://" + hostName + ":8888/" + userName + "CalendarObject";
        Naming.bind(coAddr, co);
        //If load from file, user name exist, but calobj is null, don't worry, hashmap will override the old one. Hope so!
        userCalObjs.put(userName, co);
        return coAddr;*/
    }
    
    //
    private void prepareReplicate() throws AlreadyBoundException, ParseException {
    	if((type == PRIMARY) && (replica == null)) {  //If this is a primary calendar and have no replica, we replicate a new backup calendar manager for it.
    		replica = replicate();
    	}
    	
    	if(replica != null) {  //If we have a backup, then we connect all calendar objects that shared between them, actually they should have the same cos, so cos can be sychronized.
    		HashMap<String, CalendarObjectImpl> remoteCalObjs = null;
			try {  //Put into try-catch because when replica fails, we donot want throw exception to 
				remoteCalObjs = replica.list();
				Iterator<String> it = userCalObjs.keySet().iterator();
	    		while(it.hasNext()) {
	    			String u = it.next();
	    			CalendarObjectImpl myco = userCalObjs.get(u);
	    			Iterator<String> iit = remoteCalObjs.keySet().iterator();
	    			while(iit.hasNext()) {
	    				String uu = iit.next();
	    				CalendarObject rco = (CalendarObject)remoteCalObjs.get(uu);
	    				if(myco.getName().equals(rco.getName())) {
	    					myco.connectReplica(rco);
	    				}
	    			}
	    		}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
    		
    	}
    }
    
    //Copy this calendar manager to the next neighbor, as its backup calendar manager.
    private CalendarManager replicate() throws AlreadyBoundException, ParseException {
		try {
			NamingService nextNS = namingService.getNextNamingService();  //Before ring recover, may get a invalid ns? Need to make ring recover speed up and unnoticable for
			//upper level.
			if(nextNS != null) {
				CalendarManager backupCM = nextNS.replicateCalendarManager(id, this, userCalObjs, groupEvents);
				return backupCM;
			}
			else
				return null;
			
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return null;
		}   	
    }
    
    //
    private void detectReplica() {
    	if((type == PRIMARY) && (replica != null)) {  //Primary will keep monitoring if backup is alive, if not, it clear its own replica, and at the next run of this thread, it
    		                                          //will find its new neighbor and produce a new backup.
    		try {
				replica.isAlive();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				replica = null;
				Iterator<String> it = userCalObjs.keySet().iterator();
				while(it.hasNext()) {
					String u = it.next();
					CalendarObjectImpl co = userCalObjs.get(u);
					co.disconnectReplica();
				}
				System.out.println("Calendar Manager id: " + id + " type: " + type + " loses backup.");
			}
    	}
    	else if((type == BACKUP) && (primary != null)) {  //Backup will keep monitoring if primary is dead, if true, it claims itself as primary, and at the next run of this
    		                                              //thread, it will replicate itself to its next neighbor.
    		try {
				primary.isAlive();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				primary = null;
				type = PRIMARY;
				System.out.println("Calendar Manager id: " + id + " type: " + type + " loses primary.");
			}
    	}
    }
       
    public void start() {
    	isRunning = true;
    	Runnable cmTask = new Runnable() {
    		public void run() {
    			while(isRunning == true) {  				
    				try {  
    					Thread.sleep(REPLICATE_INTERVAL);  //Sleep first because at debug stage, ns create cm, start backup loop, return cm to ui, then ui create first co,    					
    					//that means, the backup loop start there is any co, so I cannot see backup co, so let backup sleep a while, leave time for co start, just for debug.
						prepareReplicate();		
						detectReplica();						
					} catch (AlreadyBoundException | ParseException | InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
    				
    				System.out.println("Calendar Manager start detecting.");
    			}
    		}
    	};
    	Thread cmTaskThread = new Thread(cmTask);
		cmTaskThread.start();
    }
    
    public void stop() throws IOException {
    	isRunning = false;
        	
		Iterator<String> it = userCalObjs.keySet().iterator();
		while(it.hasNext()) {
			CalendarObjectImpl tmp = userCalObjs.get(it.next());
			if(tmp != null)
				tmp.saveData();
		}
    	
		saveData();  
    }
    
    @Override
    //Only used by backup. Copy primary data.
	public void setCalObjs(HashMap<String, CalendarObjectImpl> remoteCalObjs)
			throws RemoteException, AlreadyBoundException, ParseException {
		// TODO Auto-generated method stub
		Iterator<String> it = remoteCalObjs.keySet().iterator();
		while(it.hasNext()) {
			String u = it.next();
			CalendarObject remoteCO = remoteCalObjs.get(u);
			CalendarObjectImpl myCO = createCalendar(u, null);
			myCO.setEvents(remoteCO.getEvents());
			userCalObjs.put(u, myCO);
		}

		try {
			saveData();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	@Override
	//Only used by backup. Copy primary data.
	public void setGroupEvents(HashMap<String, ArrayList<String>> remoteGroupEvents)
			throws RemoteException {
		// TODO Auto-generated method stub
		Iterator<String> it = remoteGroupEvents.keySet().iterator();
		while(it.hasNext()) {
			String g = it.next();
			ArrayList<String> ps = remoteGroupEvents.get(g);
			ArrayList<String> myps = new ArrayList<String>();
			Iterator<String> iit = ps.iterator();
			while(iit.hasNext()) {
				myps.add(iit.next());
			}
			groupEvents.put(g, myps);
		}

		try {
			saveData();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
    
	@Override
    public HashMap<String, CalendarObjectImpl> list() throws RemoteException {
    	return userCalObjs;
    }
    
    //This method don't have to be an interface, because it is only be called locally.
	//Only connect local calendar objects.
    //public CalendarObjectImpl connectCalendar(String userName) {
    public CalendarObjectImpl connectCalendar(String userName) {
    	//Synchronize
    	//synchronized(userCalObjsLock) {
    	
    	CalendarObjectImpl res = null;
    	//First locally look up.	
    	if(userCalObjs.containsKey(userName) && (userCalObjs.get(userName) != null)) {
    		res = userCalObjs.get(userName);
    		//System.out.println(res.name);
    		return res;
    	}
    	else {
    		return null;
    	}
   	    //}  //End of sync.
    }
    
    //Actually "remote" is misunderstanding, this function can look for all cos, but just return interfaces.
    public CalendarObject connectRemoteCalendar(String userName) throws RemoteException {
    	int trackers[] = {1, 2, 3, 5, 6};  //Hardcode here, not good!
		CalendarServiceLocator csl = new CalendarServiceLocator(trackers);
        CalendarManager cm = csl.lookup(userName);
        if(cm != null) {
        	HashMap<String, CalendarObjectImpl> remoteUsers = cm.list();
        	Iterator<String> it = remoteUsers.keySet().iterator();
        	while(it.hasNext()) {
        		String n = it.next();
        		if(n.equals(userName))
        			return (CalendarObject)remoteUsers.get(n);
        	}
        }
        
        return null;
    }
    
    //For debug.
    public void debugGroupEvents() {
    	//For debug.
    	Iterator it = groupEvents.entrySet().iterator();
    	while(it.hasNext()) {
    		Map.Entry entry = (Map.Entry)it.next();
    		String d = (String) entry.getKey();
    		ArrayList<String> u = (ArrayList<String>) entry.getValue();
    		System.out.println("GroupEvent:");
    		System.out.println(d + " -- " + u);
    	}
    	System.out.println();
    }
    
    //For debug.
    public void debugUserCalObjs() {
    	Iterator it = userCalObjs.entrySet().iterator();
    	while(it.hasNext()) {
    		Map.Entry entry = (Map.Entry)it.next();
    		String user = (String)entry.getKey();
    		
    		//Offline issue.
    		//String obj = (((CalendarObjectImpl)entry.getValue()).hasUI()) ? "Something" : "NULL";
    		String obj = (((CalendarObjectImpl)entry.getValue()) != null) ? "Something" : "NULL";
    		System.out.println(user + " " + obj);
    	}
    	System.out.println();
    }
    
    //Add group event into groupEvents.
    @Override
    public void addGroupEvent(String event, ArrayList<String> users) throws RemoteException {
    	//Synchronize
    	//synchronized(groupEventsLock) {
    		
    	groupEvents.put(event, users);
    	if((type == PRIMARY) && (replica != null))  //If we modify userObjs or groupEvent, we should sychronize such actions to replica.
    		replica.addGroupEvent(event, users);
    	System.out.println("Added group event: " + event + " " + users);

		try {
			saveData();  //Any time we change data, we save it to file.
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	//}  //End of sync
    	//debugGroupEvents();
    }
    
    /*public void removeFromGroup(String des, String user) {
    	if(groupEvents.containsKey(des)) {
    		//System.out.println("In removeFromGroup: " + des + " " + user + "\n");
    		ArrayList<String> users = groupEvents.get(des);
    		//Remove user from group.
    		Iterator<String> it = users.iterator();
    		while(it.hasNext()) {
    			String tmp = it.next();
    			//System.out.println(tmp);
    			if(tmp.equals(user)) {  //String comparison must use equals(), not ==.
    				it.remove();
    				//System.out.println(user + " is removed.\n");
    				break;
    			}
    		}
    		//If group is empty, remove group
    		if(users.isEmpty()) {   			
    			groupEvents.remove(des);
    			System.out.println("Remove " + des + " from groupevents.\n");
    		}
    	}  
    	
    	debugGroupEvents();
    }*/
    
    //Delete group event from every member of that group, then remove group event from groupEvents.
    @Override
    public void removeGroup(Event e, String owner) throws RemoteException {
    	//Synchronize
    	/*synchronized(groupEventsLock) {
        synchronized(userCalObjsLock) {*/
    		
    	if(groupEvents.containsKey(e.getDescription())) {
	    	ArrayList<String> users = groupEvents.get(e.getDescription());
	    	Iterator<String> it = users.iterator();
	    	while(it.hasNext()) {
	    		String mem = it.next();
	    		//CalendarObjectImpl co = userCalObjs.get(mem);
	    		CalendarObject co = connectRemoteCalendar(mem);
	    		if((co != null) && (!co.getName().equals(owner)))
	    			co.deleteEvent(e.getBeginCalendar());
	    	}
	    	groupEvents.remove(e.getDescription());
	    	System.out.println("Remove group event: " + e.getDescription());
	    	//debugGroupEvents();

    		try {
				saveData();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    	}  
    	
        //}
    	//}  //End of sync
    }
    
    //Save CalendarManager data to file.
    public void saveData() throws IOException {     
    	String fileName = getFileName(id, type);
    	CalendarManagerData.save(userCalObjs, groupEvents, fileName);
    	System.out.println("Save Calendar Manager: " + id + "-" + type + "'s data to " + fileName);
    }
    
    //Handle log out rquest from a user..
    public void logOut(String user) throws IOException, NotBoundException {
    	//Synchronize
        //synchronized(userCalObjsLock) {
    	
    	CalendarObjectImpl tmp = userCalObjs.get(user);
    	//tmp.saveData();  //Save data for the CalendarObjectImpl.
    	//tmp = null;  //This cannot affect userCalObjs, look tmp as a pointer, you must user pointer to change something, but not change pointer itself.
    	
    	//Offline issue.
    	tmp.setUI(null);  //Label this user as offline, but still keep the CalendarObjectImpl object alive. Notificator will stop too.
    	/*String addr = "rmi://" + hostName + ":8888/" + user + "CalendarObject";
    	Naming.unbind(addr);*/
    	//debugUserCalObjs();
    	/*userCalObjs.put(user, null);
    	String addr = "rmi://" + hostName + ":8888/" + user + "CalendarObject";
    	Naming.unbind(addr);
    	debugUserCalObjs();*/
        //}  //End of sync
    }

	@Override
	public void isAlive() throws RemoteException {
		// TODO Auto-generated method stub		
	}
	
	private String getFileName(int id, int type) throws RemoteException {
		return namingService.getId() + "/" + id + "-" + type + "/CalendarManager-" + id + "-" + type;
	}

    /*public static void main(String argv[]) throws AlreadyBoundException, ClassNotFoundException, IOException, ParseException, NotBoundException {
		CalendarManagerImpl cm = new CalendarManagerImpl();
		
		//Manager UI.
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
        	String line = br.readLine();
        	String[] arguments = line.split(" ");
        	for(int i = 0; i < arguments.length; i++)
        		System.out.println(arguments[i]);
        	if(arguments[0].equals("quit")) {
        		//Force CalendarObjectImpls to save their data.
        		//Place before cm savedata, because it will clean up userCalObjs.
        		//Synchronize
            	synchronized(cm.getGroupEventsLock()) {
                synchronized(cm.getUserCalObjsLock()) {
                	
        		Iterator<String> it = cm.userCalObjs.keySet().iterator();
        		while(it.hasNext()) {
        			CalendarObjectImpl tmp = cm.userCalObjs.get(it.next());
        			if(tmp != null)
        				tmp.saveData();  //Save all CalendarObjects' data.
        		}
        		
                }
            	}  //End of sync
            	
        		cm.saveData();  //Save CalendarManager data.      		
        		System.exit(0);
        	}
        }
    }*/
    
}
