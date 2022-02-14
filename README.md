# Drawlio
A multiplayer word-guessing game (heavily) inspired by _scribble.io_,  _Draw Something_ and _Pictionary_.

Players take turns being the drawer, where they are tasked to draw a given word that only they know. While drawing, the remaining players work against each other by guessing the word being drawn faster than the others.  
The round ends when there's only one player who hasn't guessed correctly, after which points are distributed to guessers based on the order they guessed correctly (i.e. fastest player gets most points). In rounds with only two players the round ends when the guesser guess correctly.

Originally written for Java 8 but is now updated for Java 17.  
I have only tested with `OpenJDK Temurin-17.0.1+12`.

## Disclaimer
This project was made as an exercise in Java network programming and not meant for anyone to actually play. I put bare minimum effort in the visuals/UI and there's likely some bugs.

## Example
An example game with four players. _Player 4_ left and _Player 5_ joined and could immediately start making guesses in the middle of the round without issues.
![Example](/example.png)

## Server
Listens for new client connections, handles the game state/logic and handles all communication with the clients.

Compile
```
javac -d bin ./src/drawlio/server/*.java
```

Run
```
java -cp bin drawlio.server.ServerLauncher
```
Port to listen on can optionally be supplied as an argument (defaults to 50505)
```
java -cp bin drawlio.server.ServerLauncher 12345
```

## Client
Compile
```
javac -d bin ./src/drawlio/client/*.java
```

Run
```
java -cp bin drawlio.client.ClientLauncher
```
Server address/domain and port can optionally be supplied as arguments (defaults to 127.0.0.1 and 50505)
```
java -cp bin drawlio.client.ClientLauncher 10.1.2.3 12345
```
```
java -cp bin drawlio.client.ClientLauncher example.com 2121
```