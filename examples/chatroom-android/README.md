# Muwyn Chat-Room Example

A chat-room app for Android showing how to interact with a server running Muwyn.

Demo:

<a href="http://www.youtube.com/watch?feature=player_embedded&v=zbPXfrN5m00
" target="_blank"><img src="http://img.youtube.com/vi/zbPXfrN5m00/0.jpg" 
alt="Muwyn Chat-Room Example Video" width="240" height="180" border="10" /></a>


## Running the server

1. Go to the root dir of this sample
2. ` $ cd server-dropwizard`
3. ` $ ..\gradlew run`

The above code will start a local server on port 8080

## Running the clients

They can be launched from Android Studio. Just make sure you have the right
ip on the `RetrofitRestApi` class.

## Implementation details

### Server

The communication between the clients and the server is done through a REST API where clients can `GET` or `POST` commits.

Each chat-room will get its own "resource id". This means each chat-room will have its own commit's sequence.

### Client

#### Offline usage

The client was designed to work both online and offline. Whenever a commit is made, it is persisted locally.
Each commit stored also marks if it has been synced with the cloud or not.

If the client is online, then it will try to push the commits that has not been sent to the cloud yet.
If it succeed, then it will mark those changes locally as synced and will not resend again.

#### Recover from `OutOfSync` strategy

The client can adopt many different strategies to recover from an `OutOfSync` response.
Different apps will want to do it differently.

For this app, when the user posts a message to a chat-room while offline and can't sync
because of `OutOfSync` when it goes online, the client will keep a copy to those posts somewhere.
Then, it will do a normal sync fetching the changes it missed.
Once the client is up-to-date, then it will let the user decides if these posts it saved before
should be sent again or deleted.

#### Recover from others error responses

The others error responses may mean the client has a bug and reached some kind of invalid state, or
sent a commit that makes no sense. At this point the client just completely deletes its
local cache and request all the commits from start. The server should return a
correct version of the data.

#### When to sync

For now the client will try to sync every `X` milliseconds. It is not to be used in production but okay for simplicity and local usage.

One possible way for the server to notify the client when a resource changed is using services like GCM or Firebase.
If continuous changes are made to a resource, like a heavily visited chat-room, then sockets might be a better solution.

# TODO

* Support for snapshots
* Better error handling on the client

# License

Muwyn is published under the Apache 2.0 license.