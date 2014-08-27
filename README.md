DistributedCalendar
===================
This is a distributed calendar system. For more details you can refer to CS675-proj2.pdf.

1. Use ./compile to compile all java source code, and user ./clean to clean all .class files.
2. Usage:
   Server -- Start 5 service on five different nodes, for example, 1 - 5, using command: java -Djava.security.policy=policy NamingServiceImpl bootstrap pprev p id n nnext, 
   where pprev means your previous neighbor's previous neighbor id, p means you previous neighbor node id, id means this node's id, and n and nnext have the same meaning.
   Client -- Using command: java -Djava.security.policy=policy CalendarUIImpl username 1 2 3 5 6. 1-6 here means the server node ids that user already know.
