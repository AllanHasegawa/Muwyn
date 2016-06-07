# Muwyn

> Project is in very early alpha stage (more like proof of concept). Don't use it in production.

Muwyn is a **Mu**lti-**w**ay Incremental S**yn**chronization framework.

<img alt="Muwyn" title="Muwyn" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_muwyn.svg" height="256">

A *single-way* synchronization means information goes one way only, e.g. cloud to device (ex: Android Phone).
A *two-way* synchronization allows for a device not only fetch data from the server, but to also push to it.
A **multi-way** synchronization allows for multiple devices to do **two-way** synchronization each, as in the figure above.

The **incremental** part means that when a resource (ex: a user profile, a chatroom, a favorite list, etc...) changes,
the synchronization will not send the entire resource to the user, but only the changes that occurred.

# Docs

[TODO]

# Examples

 * [Chat-Room](examples/chatroom-android)

# How it works?

## Centralized Version control

Every incremental change works like a *diff* in *git*. An incremental change is named **commit** for simplicity.
The clients can *push/pull* commits from the server.

Differently than git, this solution assumes a centralized server to hold the "true state" of a resource.
This means that all clients/users will try to always be in sync with it, and if something goes wrong,
the server will be the one holding the valid sequence of commits.
The server will also validate ALL the commits before persisting and sending to the clients.

The version control is enforced by processing all commits one at a time in the server. Also,
the client wanting to push a commit must specify the ID of the previous commit to make sure
the ordering is correct.

For example, on a chat-room resource we could have three incremental changes types: Join, Leave, Post. A valid sequence would look like:

    (seq=0, id=HJK, type=Join, userId=Alice)
    (seq=1, id=QWE, type=Post, userId=Alice, message=Hello, World!)
    (seq=2, id=UIO, type=Leave, userId=Alice)

In the above sequence, Alice joins the chatroom, sends a message and leave. Because each change has a version control with a sequential number and an id,
Alice can be certain if she has the latest version by comparing it to the server. If both have `(seq=2, id=UIO)` as latest version, then both are in sync.
The following section will show all the different messages that this tuple allows for.

## Messages

The following subsections will describe the different messages a client (Alice) can interact with the server.

Each message will have a occompanying example using a REST API for a Chat-room.
Note that these REST messages are not exactly the same as the ones implemented in our example.

The chatroom resource can accept three actions: Join, Leave, Post. Each action is mapped to a commit.

### Get commits

#### Get commmits full

Clients can request all the commits on a particular resource. This is useful to reset the state of the client.

<img alt="Get Full" title="Get Full" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_get_full.svg" height="100">

##### Alice request:

    GET (v1/chatrooms/Canada/increments?versionSequence=-1&versionId=-1)

##### Server response:

    {
      "type": "GetIncs",
      "previousVersion": { "sequence": -1, "id": "-1" },
      "increments": [
        {"id": "h", "type": "Join", "userId": "Alice"},
        {"id": "w", "type": "Post", "userId": "Alice", "message": "Hello, World!"},
        {"id": "k", "type": "Leave", "userId": "Alice"}
      ]
    }

#### Get commits partial

When a client already has some commits, it can request just a partial list of changes.

<img alt="Get Partial" title="Get Partial" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_get_partial.svg" height="100">

##### Alice request:

    GET (v1/chatrooms/Canada/increments?versionSequence=0&versionId=h)

##### Server response:

    {
      "type": "GetIncs",
      "previousVersion": { "sequence": 0, "id": "h" },
      "increments": [
        {"id": "w", "type": "Post", "userId": "Alice", "message": "Hello, World!"},
        {"id": "k", "type": "Leave", "userId": "Alice"}
      ]
    }

---

### Post commits

Clients can push commits too!

<img alt="Post" title="Post" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_post.svg" height="100">

##### Alice request:

    POST (v1/chatrooms/Canada/users/Alice/increments)
    {
      "latestVersion": { "sequence": 0, "id": "h" },
      "userId": "Alice",
      "chatRoomId": "Canada",
      "increments": [
        {"id": "w", "type": "Post", "userId": "Alice", "message": "Hello, World!"},
        {"id": "k", "type": "Leave", "userId": "Alice"}
      ]
    }

##### Server response:

    {
      "type": "PutIncs",
      "latestVersion": { "sequence": 2, "id": "k" },
      "ackIncIds": ["w", "k"]
    }

---

### What if it goes wrong?

### OutOfSync

`OutOfSync` is used to tell the client an error happened when it tried to push commits while not having the latest version.

<img alt="OutOfSync - client behind post" title="OutOfSync - client behind post" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_outofsync_client_behind_post.svg" height="100">

##### Alice request:

    POST (v1/chatrooms/Canada/users/Alice/increments)
    {
      "latestVersion": { "sequence": 1, "id": "w" },
      "userId": "Alice",
      "chatRoomId": "Canada",
      "increments": [
        {"id": "j", "type": "Leave", "userId": "Alice"}
      ]
    }

##### Server response:

    {
      "type": "OutOfSync",
      "latestVersion": { "sequence": 2, "id": "k" }
    }

---

### InvalidState

On each commit, the server must validate the request. If a commit invalidates the state on a resource, then don't do anything
with it but tell the client. The client can then take actions like resetting its local database.

It is also a form of protection against malicious apps disguised as a trusted app.

In the following example, the client is trying to `Join` twice in the same chatroom.

<img alt="InvalidState" title="InvalidState" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_invalidstate.svg" height="100">

##### Alice request:

    POST (v1/chatrooms/Canada/users/Alice/increments)
    {
      "latestVersion": { "sequence": 0, "id": "h" },
      "userId": "Alice",
      "chatRoomId": "Canada",
      "increments": [
        {"id": "w", "type": "Post", "userId": "Alice", "message": "Hello, World!"},
        {"id": "a", "type": "Join", "userId": "Alice"}
      ]
    }

##### Server response:

    {
      "type": "InvalidState",
      "latestVersion": { "sequence": 1, "id": "k" },
      "ackIncIds": ["w"]
    }

---

### SequenceBroken

`SequenceBroken` is another response designed to deal with malicious or buggy clients.
It happens when the client tries to push/pull from a commit not on the server.
The most common reason is when the client is ahead of the server and tries to sync.

Note that a client should keep track of the changes it sends to the server and send them
ir order. In this particular example, the client should have sent the message `(1,a)` and
get an `OutOfSync` message. But, by sending a message ahead and a previous message
with a wrong `id`, then something is very wrong and causes a `SequenceBroken`.

<img alt="SequenceBroken" title="SequenceBroken" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_sequencebroken.svg" height="100">

##### Alice request:

    POST (v1/chatrooms/Canada/users/Alice/increments)
    {
      "latestVersion": { "sequence": 1, "id": "a" },
      "userId": "Alice",
      "chatRoomId": "Canada",
      "increments": [
        {"id": "j", "type": "Post", "userId": "Alice", "message": "Hello, Bob!"}
      ]
    }

##### Server response:

    {
      "type": "SequenceBroken",
      "latestVersion": { "sequence": 1, "id": "w" }
    }

---

### InvalidIncId

Another response to deal with problematic clients. Since each commit's `id` are created
and sent by the client, the server needs to validate it. The requirement is that they must be unique
for each resource.

<img alt="InvalidIncId" title="InvalidIncId" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_msgs_invalidincid.svg" height="100">

##### Alice request:

    POST (v1/chatrooms/Canada/users/Alice/increments)
    {
      "latestVersion": { "sequence": 0, "id": "h" },
      "userId": "Alice",
      "chatRoomId": "Canada",
      "increments": [
        {"id": "a", "type": "Post", "userId": "Alice", "message": "Hello, Bob!"},
        {"id": "a", "type": "Post", "userId": "Alice", "message": "Bob?"}
      ]
    }

##### Server response:

    {
      "type": "InvalidIncId",
      "incId": "a"
    }

---

### Give me a multi-way example!

Okay okay! here is a multi-way example :)

In the following example, the above messages are used to keep two clients in sync.
Note how Bob try to post a message while being out of sync. The server tells it to him
so he can decides what to do with the incremental change denied. Bob then save it to send
again later.

<img alt="Muwyn Example" title="Muwyn Example" src="https://cdn.rawgit.com/AranHase/Muwyn/782be8a2ead99644b37f886839b1b99ec2969b49/resources/graphics/readme/readme_muwyn_example.svg" width="256">

## Incremental changes? Why?

Let's imagine we are working on a TODO app, but lets complicate it by making it shared online with your friends.
The traditional way of doing such a thing would be to the cloud to store a list of TODOs, and each user
will then send a request to the server to change them. When a change occurs, everyone will get a fresh copy of this list.

Problem is, sending and getting the whole list every time a change occur is wasteful, specially if the list can hold thousand of items.
And, what if we want to also allow for the users to use that list offline? If we don't track what changes the user is doing to the list,
then the only way to synchronize it to the cloud would be to send the whole list to it. And this introduce the problem of the synchronization
potentially overriding changes done by others users.

A solution to this problem is to, instead of storing the list locally and in the server, we store the incremental changes made to it.
That way everyone can fetch those changes and reconstruct the list to its latest version.
More so, when a user goes offline, it can store all the changes it is doing on the list on its local database, and when it goes online it sends
only those changes. If any of those changes conflicts with others users changes, then the user can take the appropriate actions to remedy that.

### Snapshots

The *incremental changes* solution stores the changes made to the TODO list instead of the list itself. What if the list itself is small, about 10 items,
but each item has been changed over 9000 times? It would be wasteful to synchronize 9000 commits for each item. However, the server can reconstruct the TODO list
using the changes and then save the list's state. By saving the actual list, it creates a snapshot. When a fresh client connects to this resource,
instead of fetching all the changes, it can just fetch the latest snapshot and get incremental changes from there.

# License

Muwyn is published under the Apache 2.0 license.