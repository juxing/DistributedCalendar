import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class CalendarObjectImpl extends UnicastRemoteObject implements CalendarObject {
	public String name = null;  //User name.
	private TreeMap<Calendar, Event> events = null;  //User's calendar.
	private CalendarManagerImpl cm = null;  //CalendarManager.
	private CalendarUI myUI = null;  //User's interface.
	private CalendarObject replica = null;
	
	//Synchronize object for events. This object must be serializable.
	private String eventsLock = new String("Lock");
	
    public CalendarObjectImpl(String userName, CalendarManagerImpl calMgrI, CalendarUI ui) throws ParseException, NotBoundException, ClassNotFoundException, IOException {
        super();
        name = userName;
        cm = calMgrI;
        //myUI = ui;
        //Initialize calendar with a long enough open event.
        //System.setSecurityManager(new RMISecurityManager());
        //At here can't use setSecurityManager, error is access denied, don't know why, maybe because CalendarObject is created by CalendarManager, 
        //and CalendarManager has already setSecurityManager.
        //myUI = (CalendarUI)Naming.lookup(calUIAddr);
        myUI = ui;
        
        //First try to load data from file, file name is user name.
        String coName = getFileName(calMgrI.getId(), calMgrI.getType(), userName);
        CalendarObjectData data = null;
        if((data = CalendarObjectData.load(coName)) != null) {
        	events = data.getEvents();
        	System.out.println("Read Calendar Object: " + userName + " from " + coName);
        }
        //If no such file, then initialized new events.
        else {
	        Calendar pastBegin = Calendar.getInstance();
	        Calendar futureEnd = Calendar.getInstance();
	        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	        Date dt = format.parse("1970-01-01 00:00:00");  //First generate a fake and large enough open event for user to insert their own calendar.
	        pastBegin.setTime(dt);
	        dt = format.parse("2100-01-01 00:00:00");
	        futureEnd.setTime(dt);
	        String des = "Open";
	        int ac = 3;
	        Event initEvent = new Event(pastBegin, futureEnd, des, ac);
	        events = new TreeMap<Calendar, Event>();
	        events.put(pastBegin, initEvent);
        }
        
        //myUI.remind();
        //Offline issue.
        //(new Thread(new CalendarNotificator(this, myUI))).start();
        
        //debugEvents();
	    System.out.println("Calendar Object " + userName + " is created.");
    }
    
    //Save data to file.
    public void saveData() throws IOException { 
    	String fileName = getFileName(cm.getId(), cm.getType(), name);
    	CalendarObjectData.save(events, fileName);
    	System.out.println("Save Calendar Object " + name + "'s data to " + fileName);
    }
    
    //Inset an event into an open event.
    public void insertEvent(Event open, Event e) {
    	//synchronized(eventsLock) {
    		
    	//First, remove the open event we want to insert to.
    	events.remove(open.getBeginCalendar());
    	
    	//Insert head open event.
    	Calendar beginTime = open.getBeginCalendar();
    	Calendar endTime = e.getBeginCalendar();
    	if((endTime.compareTo(beginTime)) > 0) {
    		String des = "Open";
    		int ac = 3;
    		Event event = new Event(beginTime, endTime, des, ac);
    		events.put(beginTime, event);
    	}
    	   	
    	//Insert tail open event.
    	beginTime = e.getEndCalendar();
    	endTime = open.getEndCalendar();
    	if((endTime.compareTo(beginTime)) > 0) {
    		String des = "Open";
    		int ac = 3;
    		Event event = new Event(beginTime, endTime, des, ac);
    		events.put(beginTime, event);
    	}
    	
    	//Insert this event.
    	events.put(e.getBeginCalendar(), e);   	
    	
    	//}  //End of sync
    	try {
			saveData();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    }
    
    //Get an available open event, if no such event, return null.
    @Override
    public Event getAvailableOpenEvent(Event e) {
    	//synchronized(eventsLock) {
    		
    	Calendar beginTime = e.getBeginCalendar();
    	Calendar endTime = e.getEndCalendar();
    	Calendar pOpenBeginTime = events.floorKey(beginTime);
    	Event pOpenEvent = events.get(pOpenBeginTime);
    	Calendar pOpenEndTime = pOpenEvent.getEndCalendar();
    	if((pOpenEvent.getAccessCtrl() == 3) && (pOpenEndTime.compareTo(endTime) >= 0))
    		return pOpenEvent;
    	else 
    		return null;
    	
    	//}  //End of sync
    }

    //So now we only accept this kind of input:
    //schedule date date eventname 0/1
    //or schedule name1 name2...(must have two or more include yourself) date date eventname 2
    public boolean scheduleEvent(ArrayList<String> groupMems, Event e) throws RemoteException {  
    	/*if((replica != null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
    		replica.scheduleEvent(groupMems, e);
    	}*/
    	
    	//Add personal private or public event.
    	if((groupMems.size() == 1) && groupMems.get(0).equals(name) && (e.getAccessCtrl() < 2)) {
    		Event openEvent = null;
    		if((openEvent = getAvailableOpenEvent(e)) != null) {
    			insertEvent(openEvent, e);
    			//debugEvents();
    			System.out.println("Schedule event: " + e);
    			
    			//Back up personal event at replica.
    			if((replica != null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
    	    		replica.scheduleEvent(groupMems, e);
    	    	}
    			return true;
    		}
    		else
    			return false;
    	}
    	
    	//Add group event;
    	else if((groupMems.size() > 1) && (e.getAccessCtrl() == 2)){  //I am the owner of group event.   
    		//First judge if can schedule event at all users, if not, just return false.
    	    //First judge self, then other group members.
    		if(groupMems.contains(name))
    			if(getAvailableOpenEvent(e) == null) {
    				System.out.println("Schedule failed: I have no space.");
    				return false;
    			}
    		
    		//To make sure we visit each calendar object in the same order, so we sorted all calendar objects based on their hash code.
    		TreeMap<Integer, String> sequence = new TreeMap<Integer, String>();
    		Iterator<String> it = groupMems.iterator();
    		while(it.hasNext()) {
    			String n = it.next();
    			sequence.put(n.hashCode(), n);
    		}
    		//System.out.println(sequence);
    		
    		Iterator<Integer> sqit = sequence.keySet().iterator();    		
    		while(sqit.hasNext()) {
    			int hc = sqit.next();
    			String uname = sequence.get(hc);
    			//System.out.println("Check calendar object: " + hc + " " + uname);
    			//System.out.println("Debug: " + uname + "'s hashCode is " + uname.hashCode());
    			if(!uname.equals(name)) {
    				CalendarObject otherMem = cm.connectRemoteCalendar(uname);
    				if(otherMem == null) {
    					System.out.println("Schedule failed: Cannot find other members.");
    					return false;
    				}
    				else {  //Check calendar object if can schedule this group event, in the same order, before check, lock the object.
    					String lock = otherMem.getLock();
    					synchronized(lock) {
	    					if(otherMem.getAvailableOpenEvent(e) == null) {
	    						System.out.println("Schedule failed: Other members have no space.");
	    						return false;
	    					}   
    					}
    				}
    			}
    		}
    		
    		//If pass above steps, that means this event can be scheduled at all users, do it, this is two-phase commit.
    		//First add to self, then other members.
    		//You can schedule a group event even you are not going to be in this group.
    		if(groupMems.contains(name)) {
    			Event openEvent = getAvailableOpenEvent(e);
    			insertEvent(openEvent, e);
    			System.out.println("Schedule event: " + e);
    			//debugEvents();
    		}
    		Iterator<String> it2 = groupMems.iterator();
    		while(it2.hasNext()) {
    			String uname = it2.next();
    			if(!uname.equals(name)) {
    				//CalendarObjectImpl otherMem = cm.connectCalendar(uname);
    				CalendarObject otherMem = cm.connectRemoteCalendar(uname);
    				ArrayList<String> tmpgroup = new ArrayList<String>();
    				tmpgroup.add(uname);
    				otherMem.scheduleEvent(tmpgroup, e);
    				/*Event openEvent = otherMem.getAvailableOpenEvent(e);  //Directly insert group event to remote co, skip around scheduleEvent tricks.
    				otherMem.insertEvent(openEvent, e);
    				otherMem.*/
    			}
    		}
    		
    		//Register group event at cm.
    		cm.addGroupEvent(e.getDescription(), groupMems);  
    		
    		//Backup work.
    		if((replica != null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
    			ArrayList<String> tmpgroup = new ArrayList<String>();
				tmpgroup.add(name);
				replica.scheduleEvent(tmpgroup, e);
	    	}
    		
    		
    		//So only primary and backup calendar managers have group events records, and all group members and one backup calendar object have this group event.
    		//That is to say, all member calendar objects have group event, and only owner calendar manager record such group event.
    		
    		return true;   		
    	}
    	
    	else if((groupMems.size() == 1) && (e.getAccessCtrl() == 2)){  //Only members of group event, so just add this event and do nothing.
    		Event openEvent = null;
    		if((openEvent = getAvailableOpenEvent(e)) != null) {
    			insertEvent(openEvent, e);
    			System.out.println("Schedule event: " + e);
    		}
    		
    		if((replica != null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
    			ArrayList<String> tmpgroup = new ArrayList<String>();
				tmpgroup.add(name);
				replica.scheduleEvent(tmpgroup, e);
	    	}
    		return true;
    	}
    	
    	return true;
   
    	/*StringBuilder sb = new StringBuilder();
    	boolean scheFail = false;
    	//No matter what event it is, first add it to self calendar.
    	if(groupMems.contains(name)) {
	    	Calendar beginTime = e.getBeginCalendar();
	    	Calendar endTime = e.getEndCalendar();
	    	Calendar pOpenBeginTime = events.floorKey(beginTime);
	    	Event pOpenEvent = events.get(pOpenBeginTime);
	    	Calendar pOpenEndTime = pOpenEvent.getEndCalendar();
	    	if((pOpenEvent.getAccessCtrl() == 3) && (pOpenEndTime.compareTo(endTime) >= 0)) {
	    		insertEvent(pOpenEvent, e); 
	    		debugEvents();
	    	}
	    	else {
	    		sb.append("Can't schedule " + e.getDescription() + " at " + name + "'s Calendar.\n");
	    		scheFail = true;
	    	}
    	}
    	
    	//Add event to all other group members.
    	if(e.getAccessCtrl() == 2) {
	    	Iterator<String> it = groupMems.iterator();
	    	while(it.hasNext()) {
	    		String uname = it.next();
	    		if(!uname.equals(name)) {
	    			CalendarObjectImpl otherMem = cm.connectCalendar(uname);
	    			ArrayList<String> otherGrMems = new ArrayList<String>();
	    			otherGrMems.add(uname);
	    			String msg = otherMem.scheduleEvent(otherGrMems, e);
	    			if(!msg.equals("")) {
	    				sb.append(msg);
	    				scheFail =
	    		}
	    	}
	    	
	    	//Add this event to group register of CalendarManager.
    		cm.addGroupEvent(e.getDescription(), groupMems);
    	}
    	 
    	return sb.toString();*/
    	/*if(groupMems != null) {
    		CalendarObjectImpl otherMem = null;
    		for(int i = 0; i < groupMems.length; i++) {
    			otherMem = cm.connectCalendar(groupMems[i]);
    			otherMem.scheduleEvent(null, e);
    		}
    		
    		//Add this event to group register of CalendarManager.
    		ArrayList<String> group = new ArrayList<String>();
    		group.add(name); //Add itself into list first.
    		for(int i = 0 ; i < groupMems.length; i++)
    			group.add(groupMems[i]);
    		cm.addGroupEvent(e.getDescription(), group);
    	}*/      	
    }
    
    //Just check self to see if in the same group event.
    public boolean inSameGroup(Event e) { 
    	//synchronized(eventsLock) {
    		
    	Iterator it = events.entrySet().iterator();
    	while(it.hasNext()) {
    		Map.Entry entry = (Map.Entry)it.next();
    		Calendar bg = ((Event)entry.getValue()).getBeginCalendar();
    		Calendar end = ((Event)entry.getValue()).getEndCalendar();
    		String des = ((Event)entry.getValue()).getDescription();
    		int ac = ((Event)entry.getValue()).getAccessCtrl();
    		if((bg.compareTo(e.getBeginCalendar()) == 0) && (end.compareTo(e.getEndCalendar()) == 0) &&des.equals(e.getDescription()) && (ac == 2))
    			return true;
    	}
    	
    	return false;
    	
    	//}  //End of sync
    }
    
    //Stripe off open events from a calendar.
    private void stripOpenEvents(ArrayList<Event> events) {
    	Iterator<Event> it = events.iterator();
    	while(it.hasNext()) {
    		Event e = it.next();
    		if(e.getAccessCtrl() == 3)
    			it.remove();
    	}
    }
    
    public ArrayList<Event> retrieveEvent(String user, Calendar begin, Calendar end) throws RemoteException {
    	//synchronized(eventsLock) {
    		
    	ArrayList<Event> reEvents = null;
    	//Retrieve own events.
    	if(user == null) {
	    	SortedMap<Calendar, Event> subEvents = events.subMap(begin, end); //There is a issue that if end time
	    	//is equal to an event time, this event will not be returned.
	    	reEvents = new ArrayList<Event>(subEvents.values());
	    	stripOpenEvents(reEvents);
    	}
    	//Retrieve other user's events.
    	else {
    		//Here I use the model that all CalendarObjectImpl can share all the data, so they are acting with CalendarManagerImpl as a integrate whole part, and all the 
    		//CalendarObjectImpl cooperate together to filter some information from CalendarUIImpl.
    		CalendarObject otherUser = cm.connectRemoteCalendar(user);  //Online problem, think about it later.
    		
    		if(otherUser != null) {
	    		reEvents = otherUser.retrieveEvent(null, begin, end);  //Get all the events from another CalendarObjectImpl.
	    		
	    		//Filter restrict events.
	    		for(int i = 0; i < reEvents.size(); i++) {
	    			Event tmp = reEvents.get(i);
	    			switch(tmp.getAccessCtrl()) {
	    			case 0:  //This event is private, remove it. 
	    				reEvents.remove(i);
	    				i--;
	    				break;
	    			case 1:  //Public event, keep it.
	    				break;
	    			case 2:  //Group event
	    				if(inSameGroup(tmp)) {  //If I has the same group event, then I must be in the same group.
	    					break;
	    				}
	    				else {  //If not in the same group, keep user from reading description of such event.
	    					Event newEvent = new Event(tmp.getBeginCalendar(), tmp.getEndCalendar(), "UNKNOWN", 2);
	    					reEvents.remove(i);
	    					reEvents.add(i, newEvent);
	    					//tmp.setDescription("UNKNOWN"); //In java, all objects are reference, so change tmp can also change reEvents.
	    				}
	    				break;
	    			case 3:  //Open event, remove it.
	    				reEvents.remove(i);
	    				i--;
	    				break;
	    			}
	    		}
    		}
    	}
    	
    	return reEvents;
    	
    	//}  //End of sync
    }
    
    public boolean deleteEvent(Calendar begin) throws RemoteException {    	
    	if((replica != null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
    		replica.deleteEvent(begin);
    	}
    	
    	//synchronized(eventsLock) {
    		
    	//Can't delete open event, but open event condition has been included in below code.      	
    	if(events.containsKey(begin)) {  //If calendar has such event.
    		
    		
			Calendar lowerKey = null;
    		Calendar higherKey = null;
    		Event lowerEvent = null;
    		Event higherEvent = null;
    		/*Event lowerNewEvent = null;
    		Event higherNewEvent = null;*/ 
    		
    		Event e = events.get(begin);
    		if(e.getAccessCtrl() == 3)  //If is an open event, leave it alone.
    			return false;
    		//First delete this event from self.   		
    		//Get head and tail events.
    		if((lowerKey = events.lowerKey(begin)) != null)
    			lowerEvent = events.get(lowerKey);
    		if((higherKey = events.higherKey(begin)) != null)
    			higherEvent = events.get(higherKey);
    		
    		//If head and tail events are both open, then merge three events into one open event.
    		if((lowerEvent != null) && (lowerEvent.getAccessCtrl() == 3) && (higherEvent != null) && (higherEvent.getAccessCtrl() == 3)) {
    			Calendar newBegin = lowerEvent.getBeginCalendar();
    			Calendar newEnd = higherEvent.getEndCalendar();
    			String des = "Open";
    			int ac = 3;
    			Event newEvent = new Event(newBegin, newEnd, des, ac);
    			
    			events.remove(lowerKey);
    			events.remove(begin);
    			events.remove(higherKey);
    			events.put(newBegin, newEvent);
    		}    
    		//If head event is open, then merge into one open event.
    		else if((lowerEvent != null) && (lowerEvent.getAccessCtrl() == 3)) {
    			Calendar newBegin = lowerEvent.getBeginCalendar();
    			Calendar newEnd = e.getEndCalendar();
    			String des = "Open";
    			int ac = 3;
    			Event newEvent = new Event(newBegin, newEnd, des, ac);
    			
    			events.remove(lowerKey);
    			events.remove(begin);
    			events.put(newBegin, newEvent);
    		}
    		//If tail event is open, then merge into one open event.
    		else if((higherEvent != null) && (higherEvent.getAccessCtrl() == 3)) {
    			Calendar newBegin = e.getBeginCalendar();
    			Calendar newEnd = higherEvent.getEndCalendar();
    			String des = "Open";
    			int ac = 3;
    			Event newEvent = new Event(newBegin, newEnd, des, ac);
    			
    			events.remove(begin);
    			events.remove(higherKey);
    			events.put(newBegin, newEvent);
    		}
    		//If head and tail are both not open, then just replace itself with an open event.
    		else {
    			e.setDescription("Open");  //It's a reference.
    			e.setAccessCtrl(3);
    		} 
    		
    		System.out.println("Delete event: " + e);
    		//Now delete from all group members if is a group event.
    		//When delete group event, tell CalendarManager to delete this user from group event.
    		//Now I decide to directly delete the whole group event from system.
    		
    		//This removeGroup function to delete group events from other members except owner self, because owner has already did that, 
    		//also only owner can delete the group event because only the owner's calendar manager has the group events record.
    		if(e.getAccessCtrl() == 2) {
    			cm.removeGroup(e, name);
    			//System.out.println("removeFromGroup: " + name + "\n");
    		}
    		
    		//debugEvents();
    		System.out.println("I have such event.");
    		
    		try {
				saveData();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		
    		return true;
    	}

    	System.out.println("I do no have such event.");
    	return false;   	
    	//}  //End of sync.
    }
    
    //For debug, output all events.
    private void debugEvents() {   	
		System.out.println(name + " events: ");
		Iterator<Calendar> it = events.keySet().iterator();
		while(it.hasNext()) {
			Calendar iKey = it.next();
			System.out.print(events.get(iKey).getBeginCalendar().getTime() + " ");
			System.out.print(events.get(iKey).getEndCalendar().getTime() + " ");
			System.out.print(events.get(iKey).getDescription() + " ");
			System.out.println(events.get(iKey).getAccessCtrl());					
		}   
		System.out.println();
    }
    
    //Get the nearest next event.
    public Event getUpComingEvent(Calendar currTime) {
    	//synchronized(eventsLock) { 
    		
    	Calendar nextCalendar = events.higherKey(currTime);
    	if(nextCalendar != null)
    		return events.get(nextCalendar);
    	else
    		return null;
    	
    	//}  //End of sync
    }
    
    //Offline issue. Detect if use is online.
    public boolean hasUI() throws RemoteException {
    	if(myUI != null)
    		return true;
    	else
    		return false;    				
    }
    
    //Combine CalendarObject with CalendarUI.
    public void setUI(CalendarUI u) {
    	myUI = u;
    	//Insert notification here!
    	if(u != null)  //When log out, Calendar manager will set ui to null, do not start notificator here.
    		(new Thread(new CalendarNotificator(this, myUI))).start();
    }

	@Override
	public TreeMap<Calendar, Event> getEvents() throws RemoteException {
		// TODO Auto-generated method stub
		return events;
	}

	@Override
	public void setEvents(TreeMap<Calendar, Event> remoteEvents)
			throws RemoteException {
		// TODO Auto-generated method stub
		events = new TreeMap<Calendar, Event>();
		Iterator<Calendar> it = remoteEvents.keySet().iterator();
		while(it.hasNext()) {
			Calendar c = it.next();
			Event e = remoteEvents.get(c);
			events.put(c, e);
		}
	}

	@Override
	public String getName() throws RemoteException {
		// TODO Auto-generated method stub
		return name;
	}
	
	public void connectReplica(CalendarObject rco) {
		if((replica == null) && (cm.getType() == CalendarManagerImpl.PRIMARY)) {
			replica = rco;
		}
	}
	
	public void disconnectReplica() {
		replica = null;
	}
	
	private String getFileName(int id, int type, String name) throws RemoteException {
		int nsId = cm.namingService.getId();
		return nsId + "/" + id + "-" + type + "/CalendarObject-" + name;
	}

	@Override
	public String getLock() throws RemoteException {
		// TODO Auto-generated method stub
		return eventsLock;
	}

}
