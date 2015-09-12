README
===========
Jing Guo (jg3527)

Implement part1, part2, bonus part.

Design
=======
Classes:

Debug: For debuging.
DV: For file initialization, paths calculation using Bellman-Ford algorithm with posion reverse and handle commands.
UserInputListener: Used to listen to the user's input and do things accordingly.
SocketListener: Used to listen to the port and receive distance vectors and other informations from neighbors.
ReceiveListener: Used to implement the heartbeat mechanism for neighbors.
SendDVThread: Used to send distance vectors to neighbors(with direct link) per timeout(defined in the file).
ShutDownWork: Used to close sockets before the program exit.
EndPackage: Constructed by the last package of file tranfer.
Header: Constructed by the data of file transfer pakage.
Node: A IP address combined with a port number described as a node.
Neighbor: The neighbors this node can reach.
Transfer: Used to do file transfer.
MyProtocol: Strings and integers used in my protocol are defined here.

Details of data structure please see the comments in the program.

Design for the reliable file tranfer:
1. I add checksum in the pakage, when receiving a package the client will first check is it valid or not. If not just ignore it, otherwise send ack back to the previous node.
2. The node will not transfer the next package until it received the ack from next node, after send out the package it will wait sometime, if did not receive the ack package, it will resend this package.
3. At the end of tranfer, the start client will pass an end package to next hop including the filename.

How to run
===========

1. Modify the directory of the received file(String RVDFileDir).
3. Open a terminal, type "cd <The dir of these files>"
4. Type "make"
5. Type java DV <File name>

Sample Run
==========
Part 1 
The details of the information showes on the terminal are in samplerun/bellman-ford.
I used the case the homework described to initialize all four nodes.
1)I first start all of them, then in terminal for client0 type showrt.
2)linkdown 192.168.56.1 30051
3)linkup 192.168.56.1 30051
4)changecose 192.168.56.1 30051 60

Part2
The details of the information showes on the terminal are in samplerun/filetransfer.
I first ask client0 to tranfer DV.txt to client1, then asked client0 to tranfer DV.txt to client2.
