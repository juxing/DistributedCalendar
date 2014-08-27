import java.io.BufferedReader;
import java.io.File;
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

public class NamingServiceImpl extends UnicastRemoteObject implements NamingService {

	/**
	 * @param args
	 */
	public static final int NSPORT = 10000;
	private static final int DETECT_INTERVAL = 2000;  //Make it half of Calendar Manager detect interval, because I want to make the ring recover faster than Calendar Manager.
	private static final int STARTUP_DELAY = 5000;
	//private static final int CMCAPACITY = 100;
	
	private int id = -1;
	//private int calendarManagerPort = -1;
	private NamingService[] prevs = null;  //Prev 2 neighbors.
	private NamingService[] nexts = null;  //Next 2 neighbors.
	private boolean running = false;  //To control nameing service threads.
	private String namingServiceAddr = null;
	private ArrayList<CalendarManagerImpl> calendarMgrs = null;  //All calendar managers controlled by this naming service.
	
	public NamingServiceImpl(int id) throws RemoteException, UnknownHostException, MalformedURLException, AlreadyBoundException, InterruptedException {
		this.id = id;
		//calendarManagerPort = NSPORT;
		prevs = new NamingService[2];
		nexts = new NamingService[2];
		calendarMgrs = new ArrayList<CalendarManagerImpl>();
		
		System.setSecurityManager(new RMISecurityManager());
        LocateRegistry.createRegistry(NSPORT);
        InetAddress inet = InetAddress.getLocalHost();
        String hostName = inet.getCanonicalHostName();
        namingServiceAddr = "rmi://" + hostName + ":" + Integer.toString(NSPORT) + "/NamingService";
        Naming.bind(namingServiceAddr, this);               
	}
	
	//The very beginning of five servers start up, all five servers must alive at the beginning, now we test with node 1, 2, 3, 5, 6.
	public void bootStrap(int p, int pp, int n, int nn) throws InterruptedException {	
		Thread.sleep(STARTUP_DELAY);  //Leave enough time for all 5 naming servers start.
				
	    String prevNSAddr = "rmi://compute-0-" + Integer.toString(p) + ".local:" + Integer.toString(NSPORT) + "/NamingService";
	    try {
	    	NamingService nsp = (NamingService)Naming.lookup(prevNSAddr);
			prevs[0] = nsp;
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Fail to add " + p);
			System.exit(0);
		}
	        
	    String prevPNSAddr = "rmi://compute-0-" + Integer.toString(pp) + ".local:" + Integer.toString(NSPORT) + "/NamingService";
	    try {
			NamingService nspp = (NamingService)Naming.lookup(prevPNSAddr);
			prevs[1] = nspp;
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Fail to add " + pp);
			System.exit(0);
		}
	        
	    String nextNSAddr = "rmi://compute-0-" + Integer.toString(n) + ".local:" + Integer.toString(NSPORT) + "/NamingService";
	    try {
			NamingService nsn = (NamingService)Naming.lookup(nextNSAddr);
			nexts[0] = nsn;
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Fail to add " + n);
			System.exit(0);
		}
	        
	    String nextNNSAddr = "rmi://compute-0-" + Integer.toString(nn) + ".local:" + Integer.toString(NSPORT) + "/NamingService";
	    try {
			NamingService nsnn = (NamingService)Naming.lookup(nextNNSAddr);
			nexts[1] = nsnn;
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Fail to add " + nn);
			System.exit(0);
		}
	}
	
	//If a node fails and join back later.
	public void joinRing(int[] trackers) throws RemoteException {
		int pos = -1;	
		for(int i = 0; i < trackers.length; i++) {
			if(trackers[i] == id) {
				pos = i;  //Record this node's position in the ring.
				break;
			}
		}
		
		int[] prevNums = new int[4];  //Record the prev and next 4 possible node ids in the ring.
		int[] nextNums = new int[4];
		for(int i = 0; i < trackers.length-1; i++) {
			int p = pos - i - 1;
			if(p < 0)
				p += 5;
			prevNums[i] = trackers[p];
			
			int n = pos + i + 1;
			if(n > 4)
				n -= 5;
			nextNums[i] = trackers[n];
		}
		
		NamingService prev = null;  //Try to find an alive prev node.
		for(int i = 0; i < prevNums.length; i++) {
			String prevNSAddr = "rmi://compute-0-" + Integer.toString(prevNums[i]) + ".local:" + Integer.toString(NSPORT) + "/NamingService";
			try {
				prev = (NamingService)Naming.lookup(prevNSAddr);
				break;
			} catch (MalformedURLException | RemoteException
					| NotBoundException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				System.out.println("Node " + prevNums[i] + " is not alive.");
			}
		}
		
		if(prev != null) {  //If we find one alive prev node, notify the prev node that we are in, also modify own states.
			updatePrevs(prev);  
			try {
				NamingService[] newNexts = prev.getNexts();
				nexts[0] = newNexts[0];
				nexts[1] = newNexts[1];
				if(nexts[0] == null)
					nexts[0] = prev;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			prev.joinProcess(this);
		}
		
		debugPrevNeighbors();
	}
	
	@Override
	public void joinProcess(NamingService ns) throws RemoteException {
		// TODO Auto-generated method stub
		NamingService[] newNexts = ns.getNexts();
		nexts[0] = ns;
		nexts[1] = newNexts[0];
	}
	
	//Naming service running daemon thread to fulfill failure detect task, if a node fails, left nodes can still form a ring.
	public void start() {
		running = true;		
		Runnable failureDetectTask = new Runnable() {
			public void run() {
				while(running == true) {
					try {
						failureDetect();
						debugNextNeighbors();
					} catch (RemoteException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}		
					
					try {
						Thread.sleep(DETECT_INTERVAL);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}					
				}
			}
		};
		Thread failureDecThread = new Thread(failureDetectTask);
		failureDecThread.start();
	}
	
	private void failureDetect() throws RemoteException {
		NamingService next = null;
		for(int i = 0; i < 2; i++) {
			next = nexts[i];
			if(isValidNS(next))
				break;
			else {
				nexts[i] = null;
				next = null;
			}
		}
		
		if(next != null)
			updateNexts(next);
	}
	
	private boolean isValidNS(NamingService ns) {
		boolean res = false;
		if(ns == null)
			res = false;
		else {
			try {
				int remoteId = ns.getId();
				if(remoteId == id)
					res = false;
				else
					res = true;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				res = false;
			}
		}
		
		return res;
	}
	
	private void updateNexts(NamingService next) {
		nexts[0] = next;
		NamingService[] nextNexts = null;
		try {
			nextNexts = next.getNexts();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		NamingService nextNext = nextNexts[0];
		if(isValidNS(nextNext))
			nexts[1] = nextNext;
		else 
			nexts[1] = null;
	}
	
	private void updatePrevs(NamingService prev) {
		prevs[0] = prev;
		NamingService[] prevPrevs = null;
		try {
			prevPrevs = prev.getPrevs();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		NamingService prevPrev = prevPrevs[0];
		if(isValidNS(prevPrev))
			prevs[1] = prevPrev;
		else 
			prevs[1] = null;
	}
	
	@Override
	public NamingService[] getNexts() throws RemoteException {
		// TODO Auto-generated method stub
		return nexts;
	}
	
	@Override
	public NamingService[] getPrevs() throws RemoteException {
		// TODO Auto-generated method stub
		return prevs;
	}
	
	@Override
	public int getId() throws RemoteException {
		// TODO Auto-generated method stub
		return id;
	}
	
	public NamingService getNextNamingService() throws RemoteException {
		// TODO Auto-generated method stub
		return nexts[0];
	}
	
	//Naming service is responsible for creating calendar managers.
	@Override
	public CalendarManager createCalendarManager(int id, int type,
			CalendarManager primary) {
		// TODO Auto-generated method stub
		CalendarManagerImpl cm = null;		
		if((cm = getCalendarManager(id, type)) != null)
			return cm;

		try {
			cm = new CalendarManagerImpl(id, type, primary, this);
		} catch (ClassNotFoundException | AlreadyBoundException | IOException
				| ParseException | NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		calendarMgrs.add(cm);
		cm.start();  //Once create a calendar manager, the cm thread also has to be started.
		System.out.println("Naming Service start Calendar Manager detecting.");
		return cm;
	}
	
    //Naming service is also responsible for creating backup calendar manager for primary calendar manager that are controlled by it.
	@Override
	public CalendarManager replicateCalendarManager(int id,
			CalendarManager primary,
			HashMap<String, CalendarObjectImpl> userCalObjs,
			HashMap<String, ArrayList<String>> groupEvents)
			throws RemoteException, AlreadyBoundException, ParseException {
		// TODO Auto-generated method stub
		CalendarManager cm = createCalendarManager(id, CalendarManagerImpl.BACKUP, primary);
		cm.setCalObjs(userCalObjs);  //Will replace the content of cm that were generated by reading from files, so backup calendar manager files have no sense, backup calendar
		                             //manager is always the exact copy of primary, so each time we start servers, we read primary cms from files, and the replicate them as backups.
		cm.setGroupEvents(groupEvents);
		return cm;
	}
		
	//Look for calendar manager from local.
	private CalendarManagerImpl getCalendarManager(int id, int type) {
		for(int i = 0; i < calendarMgrs.size(); i++) {
			CalendarManagerImpl cm = calendarMgrs.get(i);
			if((cm.getId() == id) && (cm.getType() == type))
				return cm;
		}
		
		return null;
	}
	
	//Stop daemon thread, also all calendar manager's threads, including primary and backup.
	public void stop() throws NotBoundException, IOException {
		running = false;
		Naming.unbind(namingServiceAddr);
		Iterator it = calendarMgrs.iterator();
		while(it.hasNext()) {
			CalendarManagerImpl cm = (CalendarManagerImpl)it.next();
			cm.stop();
		}
	}
	
	//List all local calendar managers.
	@Override
	public ArrayList<CalendarManagerImpl> list() throws RemoteException {
		// TODO Auto-generated method stub
		return calendarMgrs;
	}
	
	private void debugNextNeighbors() throws RemoteException {
		if(nexts[0] != null)
			System.out.print("My next is " + nexts[0].getId() + ", ");
		if(nexts[1] != null)
			System.out.println("nextnext is " + nexts[1].getId());
	}
	
	private void debugPrevNeighbors() throws RemoteException {
		if(prevs[0] != null)
			System.out.print("My prev is " + prevs[0].getId() + ", ");
		if(prevs[1] != null)
			System.out.println("prevprev is " + prevs[1].getId());
	}
		
	public static void main(String[] args) throws NotBoundException, AlreadyBoundException, IOException, InterruptedException {
		// TODO Auto-generated method stub
		NamingServiceImpl ns = null;		
		String comm = args[0];
		if(comm.equals("bootstrap")) {  //Bootstrap all five naming services. java -Djava.security.policy=policy NamingServiceImpl bootstrap 2 3 5 6 1
			int id = Integer.parseInt(args[3]);
			int p = Integer.parseInt(args[2]);
			int pp = Integer.parseInt(args[1]);
			int n = Integer.parseInt(args[4]);
			int nn = Integer.parseInt(args[5]);
			ns = new NamingServiceImpl(id);
			ns.bootStrap(p, pp, n, nn);
			
			String fn = ns.getId() + "/" + ns.getId() + "-0" + "/CalendarManager-" + ns.getId() + "-0";  //Cannot start Calendar Manager 4.
			File file = new File(fn);
			if(file.exists()) {
				ns.createCalendarManager(ns.getId(), CalendarManagerImpl.PRIMARY, null);
				System.out.println("Auto start Calendar Manager: " + ns.getId() + "-0");
			}
			else
				System.out.println("No startup file: " + fn);
		}
		else if(comm.equals("join")) {  //Failed node want to rejoin the ring. java -Djava.security.policy=policy NamingServiceImpl join 
			int id = Integer.parseInt(args[1]);
			int[] trackers = new int[5];
			trackers[0] = Integer.parseInt(args[2]);
			trackers[1] = Integer.parseInt(args[3]);
			trackers[2] = Integer.parseInt(args[4]);
			trackers[3] = Integer.parseInt(args[5]);
			trackers[4] = Integer.parseInt(args[6]);
			ns = new NamingServiceImpl(id);			
			ns.joinRing(trackers);
			
			CalendarServiceLocator csl = new CalendarServiceLocator(trackers);
			CalendarManager test = csl.getCalendarManagerById(ns.getId());
			if(test != null) {
				System.out.println("CalendarManager already exist, donot start!");
			}
			else {
				//If has bakcup file has a same id with ns, start the cm, and then all cos.
				String fn = ns.getId() + "/" + ns.getId() + "-0" + "/CalendarManager-" + ns.getId() + "-0";  //Cannot start Calendar Manager 4.
				File file = new File(fn);
				if(file.exists()) {
					ns.createCalendarManager(ns.getId(), CalendarManagerImpl.PRIMARY, null);
					System.out.println("Auto start Calendar Manager: " + ns.getId() + "-0");
				}
				else
					System.out.println("No startup file: " + fn);
			}
		}

		ns.start();  //Start daemon thread.
		

		
		
		
		
		//Manager UI.
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
        	String line = br.readLine();
        	String[] arguments = line.split(" ");
        	if(arguments[0].equals("quit")) {
        		ns.stop();      		
        		System.exit(0);
        	}
        }
	}





	
}
