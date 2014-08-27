import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class CalendarUIImpl extends UnicastRemoteObject implements CalendarUI {
	public String userName = null;  //User name.
    public CalendarManager cm = null;  //Calendar manager.
    public CalendarObject co = null;  //Corresponding Calendar Object.
    private boolean UIRunning = false;  //If UI is running, for notificator to work.
    //private String hostName = null;
    public CalendarServiceLocator csl = null;
    private static final int DETECT_INTERVAL = 4000;

    public CalendarUIImpl(String name, int trackers[]) throws RemoteException, MalformedURLException, NotBoundException, AlreadyBoundException, ParseException, UnknownHostException {
        super();
        userName = name;
        //Indicate if UI is running, now is used to kill notificator thread at server, maybe disable later.
        UIRunning = true;
        
        //Naming Service
        csl = new CalendarServiceLocator(trackers);
        cm = csl.lookup(name);
        //System.out.println("Name hash value is " + ((Math.abs(userName.hashCode()) % 6) + 1) + ", Nameing Service result is " + res);
               
        //Connect to CalendarManager.
        /*System.setSecurityManager(new RMISecurityManager());
        String calManAddr = "rmi://compute-0-1.local:8888/CalendarManager";
        cm = (CalendarManager)Naming.lookup(calManAddr);*/
        
        /*I used to thought the only way to invoke remote method is to get object through nameing look up;
         * but actually, as long as object extend UnicastRemoteObject, you can just pass this object to remote side, and when its interface method is invoked, 
         * it will execute remotely,
         * not locally.
         */
        //Make self a rmi server.
        //Because of this, one machine can only start on UI.
        /*LocateRegistry.createRegistry(9999);
        InetAddress inet = InetAddress.getLocalHost();
        hostName = inet.getCanonicalHostName();
        System.out.println(hostName);
        String calUIAddr = "rmi://" + hostName + ":9999/" + userName + "CalendarUI";
        Naming.bind(calUIAddr, this);*/
        
        //Get Calendar Object address from Calendar Manager.
        if(cm != null) {
        	co = cm.createCalendar(userName, this);
        	if(co == null) {
        		System.out.println("User " + userName + " is already online.\n");
            	System.exit(0);
        	}
        }
        
        else {
        	System.out.println("Failed to locate CalendarManager.");
        	System.exit(0);
        }
        //String calObjAddr = cm.createCalendar(userName, this);
        
        /*if(calObjAddr == null) {  //User is already online.
        	System.out.println("User " + userName + " is already online.\n");
        	System.exit(0);
        }
        else
        	//Get CalendarObject.
        	co = (CalendarObject)Naming.lookup(calObjAddr);*/     
    }
    
    public void start() {
    	Runnable lookTask = new Runnable() {
    		public void run() {
    			while(UIRunning == true) {  				
    				try { 
    					relocateCalendarManager();
    					Thread.sleep(DETECT_INTERVAL);												
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
    			}
    		}
    	};
    	Thread lookTaskThread = new Thread(lookTask);
		lookTaskThread.start();
    }
    
    private void relocateCalendarManager() {
    	try {
    		if(cm != null)
    			cm.isAlive();
    		else {
    			System.exit(0);  //All Naming services are down, just quit.
    		}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			try {  
				Thread.sleep(5000);  //For fix a bug, if primary die, and because of the detect interval, the backup has not take over yet, make ui wait for a while,
				//Otherwise ui cannot find primary and then create a new one, which is wrong!
			} catch (InterruptedException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			cm = csl.lookup(userName);  //Must be a new server.
			if(cm != null) {
				try {
				    CalendarObject tmp = cm.createCalendar(userName, this);                             
				    if(tmp != null)
				    	co = tmp;
				    System.out.println("Relocate to new server.");
				} catch (RemoteException | MalformedURLException
						| AlreadyBoundException | ParseException e1) {
					// TODO Auto-generated catch block
					//e1.printStackTrace();
					System.out.println("Cannot locate CalendarManager.");
				}
			}
			else
				System.out.println("Cannot locate CalendarManager.");
		}
    }
    
    //Interface provided for notificator to notify upcoming event.
    public void remind(Event e) throws RemoteException {
    	System.out.println(e + " is happening in 1 min.\n");
    }
    
    //Before call CalendarObject's scheduleEvent, we do some input preprocess work.
    public void scheduleEvent(String[] arguments) throws RemoteException {
    	final String scheduleUsage = "Usage: schedule [user-list] xxxx-xx-xx xx:xx:xx xxxx-xx-xx xx:xx:xx EventName AccessControl(0, 1, 2)\n";
    	if(arguments.length < 7) {  //Arguments num is not right.
    		System.out.println(scheduleUsage);
    		return;
    	}
    	
    	//If the event is not a group event, so we only schedule this event at current user.
    	if((Integer.valueOf(arguments[arguments.length-1]) < 2) && (arguments.length == 7)) {
	    	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	Date d1 = null;
	    	Date d2 = null;
			try {
				d1 = format.parse(arguments[1] + " " + arguments[2]);
				d2 = format.parse(arguments[3] + " " + arguments[4]);
			} catch (ParseException e1) {
				// TODO Auto-generated catch block
				//e1.printStackTrace();
				System.out.println("Date format incorrect.\n");
				return;
			}
	       
	        Calendar begin = Calendar.getInstance();      
	        Calendar end = Calendar.getInstance();
	        begin.setTime(d1);
	        end.setTime(d2);
	        String des = arguments[5];
	        int ac = Integer.valueOf(arguments[6]);
	        /*System.out.println(d1);
	        System.out.println(d2);
	        System.out.println(des);
	        System.out.println(ac);*/                
	        Event e = new Event(begin, end, des, ac);
	        //Add itself to groupMems, so groupMems only has one user.
	        ArrayList<String> groupMems = new ArrayList<String>();
	        groupMems.add(userName);
	        //co.scheduleEvent(null, e);
	        boolean res = co.scheduleEvent(groupMems, e);
	        if(res)
	        	System.out.println("Successfully schedule " + e.getDescription() + ".\n");
	        else
	        	System.out.println("Can't schedule " + e.getDescription() + ". Please check calendar.\n");
	        	
	        /*if(co.scheduleEvent(null, e))
	        	System.out.println("Successful to schedule event.");
	        else
	        	System.out.println("Failed to schedule event.");*/
    	}
    	//This is a group event.
    	else if((Integer.valueOf(arguments[arguments.length-1]) == 2) && (arguments.length > 7)){
    		/*int userNum = Integer.valueOf(arguments[1]);
    		String[] groupMems = new String[userNum];
    		for(int i = 0; i < userNum; i++)
    			groupMems[i] = arguments[2+i];*/
    		int inputLen = arguments.length;
    		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	Date d1 = null;
	    	Date d2 = null;
			try {
				d1 = format.parse(arguments[inputLen-6] + " " + arguments[inputLen-5]);
				d2 = format.parse(arguments[inputLen-4] + " " + arguments[inputLen-3]);
			} catch (ParseException e1) {
				// TODO Auto-generated catch block
				//e1.printStackTrace();
				System.out.println("Date format incorrect.\n");
				return;
			}
			
	        Calendar begin = Calendar.getInstance();      
	        Calendar end = Calendar.getInstance();
	        begin.setTime(d1);
	        end.setTime(d2);
	        String des = arguments[inputLen-2];
	        int ac = Integer.valueOf(arguments[inputLen-1]);
	        /*System.out.println(d1);
	        System.out.println(d2);
	        System.out.println(des);
	        System.out.println(ac);*/                
	        Event e = new Event(begin, end, des, ac);
	        
	        //Fill userlist into array.
	        ArrayList<String> groupMems = new ArrayList<String>();
	        for(int i = 1; i < inputLen-6; i++)
	        	groupMems.add(arguments[i]);
	        
	        boolean res = co.scheduleEvent(groupMems, e);
	        if(res)
	        	System.out.println("Successfully schedule " + e.getDescription() + ".\n");
	        else
	        	System.out.println("Can't schedule " + e.getDescription() + ". Please check calendar.\n");
    	}
    	else {
    		System.out.println(scheduleUsage);
    		return;
    	}
    }
    
    //Before call CalendarObject's retrieveEvent, do some input preprocessing work.
    public void retrieveEvent(String[] arguments) throws RemoteException {
    	final String retrieveUsage = "Usage: retrieve [user] xxxx-xx-xx xx:xx:xx xxxx-xx-xx xx:xx:xx.\n";
    	if((arguments.length != 5) && (arguments.length != 6)) {
    		System.out.println(retrieveUsage);
    		return;
    	}
    	
    	ArrayList<Event> reEvents = null;
    	//Ignore user name, so retrieve events of self.
    	if(arguments.length == 5) {
	    	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	Date d1 = null;
	    	Date d2 = null;
			try {
				d1 = format.parse(arguments[1] + " " + arguments[2]);
				d2 = format.parse(arguments[3] + " " + arguments[4]);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				System.out.println("Date format incorrect.\n");
				return;
			} 
	        Calendar begin = Calendar.getInstance();      
	        Calendar end = Calendar.getInstance();
	        begin.setTime(d1);
	        end.setTime(d2);
	        
	        reEvents =  co.retrieveEvent(null, begin, end);
    	}
    	else {  //Retrieve a user's calendar.
    		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	Date d1 = null;
	    	Date d2 = null;
			try {
				d1 = format.parse(arguments[2] + " " + arguments[3]);
				d2 = format.parse(arguments[4] + " " + arguments[5]);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				System.out.println("Date format incorrect.\n");
				return;
			}	        
	        Calendar begin = Calendar.getInstance();      
	        Calendar end = Calendar.getInstance();
	        begin.setTime(d1);
	        end.setTime(d2);
	        
	        if(!arguments[1].equals(userName))  //Retrieve other user's calendar
	        	reEvents =  co.retrieveEvent(arguments[1], begin, end);
	        else  //Retrieve self calendar.
	        	reEvents =  co.retrieveEvent(null, begin, end);
    	}

        if((reEvents != null) && (reEvents.size() > 0)) {
        	for(int i = 0; i < reEvents.size(); i++) 
        		System.out.println(reEvents.get(i));
        	System.out.println();
        }
        else
        	System.out.println("No schedule during this time period.\n");       
    }
    
    //Can delete user own events.
    //Can't delete other people's event, except group events.
    private void deleteEvent(String[] arguments) throws RemoteException {
    	final String deleteUsage = "Usage: delete xxxx-xx-xx xx:xx:xx.\n";
    	if(arguments.length != 3) {
    		System.out.println(deleteUsage);
    		return;
    	}
    	
    	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	Date d1 = null;
		try {
			d1 = format.parse(arguments[1] + " " + arguments[2]);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Date format incorrect.\n");
			return;
		}
		
        Calendar begin = Calendar.getInstance();      
        begin.setTime(d1);   
        
        boolean res;
        if((res = co.deleteEvent(begin)) == true)
        	System.out.println("Successfully delete event.\n");
        else 
        	System.out.println("Cannot delete such event, result is " + res);
    }
    
    //UI stop, set UIRunning to false.
    private void stop() {
    	UIRunning = false;
    }
    
    //Detect if UI is stopped.
    public boolean isRunning() {
    	return UIRunning;
    }

    public static void main(String argv[]) throws NotBoundException, ParseException, AlreadyBoundException, IOException {
    	final String usage = "Usage: CalendarUIImpl username trackerNodes*" + CalendarServiceLocator.TRACKER_NUM + "\n";
    	if(argv.length != CalendarServiceLocator.TRACKER_NUM+1) {
    		System.out.println(usage);
    		System.exit(1);
    	}
    	
    	String userName = argv[0];
    	int[] trackers = new int[CalendarServiceLocator.TRACKER_NUM];
    	for(int i = 0; i < CalendarServiceLocator.TRACKER_NUM; i++) {
    		trackers[i] = Integer.parseInt(argv[i+1]);
    	}
    	
        CalendarUIImpl ui = new CalendarUIImpl(argv[0], trackers);
        System.out.println("Connected to server.\n");    
        ui.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
        	String line = br.readLine();
        	String[] arguments = line.split(" ");
        	/*for(int i = 0; i < arguments.length; i++)
        		System.out.println(arguments[i]);*/
        	if(arguments[0].equals("schedule")) {
        		ui.scheduleEvent(arguments);
        	}
        	else if(arguments[0].equals("retrieve")) {
        		ui.retrieveEvent(arguments);
        	}
        	else if(arguments[0].equals("delete")) {
        		ui.deleteEvent(arguments);
        	}
        	else if(arguments[0].equals("list")) {
        		//HashMap<String, CalendarObjectImpl> calObjs = ui.cm.list();
        		HashMap<String, CalendarObjectImpl> calObjs = ui.csl.globalList();
        		/*Set<String> set = calObjs.keySet();
        		for(String s : set)
        			System.out.println(s);*/
        		Set<String> set = calObjs.keySet();
        		Iterator<String> it = set.iterator();
        		while(it.hasNext()) {
        			String user = it.next();
        			if(((CalendarObject)calObjs.get(user)).hasUI() == true)
        				System.out.println(user + " online");
        			else
        				System.out.println(user + " offline");
        		}
        		System.out.println();
        	}
        	else if(arguments[0].equals("quit")) {
        		ui.stop();
        		ui.cm.logOut(ui.userName);
        		System.exit(0);
        	}
        	else {
        		System.out.println("Unrecognized command.\n");
        	}
        	
        	/*else if(arguments[0].equals("connect")) {
        		//The rmi server can only return the interface class to client, so the return value must be
        		//CalendarObject, can't be CalendarObjectImpl.
        		CalendarObject myco = ui.cm.connectCalendar(arguments[1]);
        		//Be careful, might copy the calendarobj to local. This just for experiment.
        		if(myco == null)
        			System.out.println("User not exist.");
        		else
        			System.out.println("Connected to calendar.");
        		
        	}*/
        }      
    }

}
