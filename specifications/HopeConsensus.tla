---------------------------- MODULE HopeConsensus ----------------------------
(* 
 * Formal TLA+ Specification for Hope Distributed Consensus Protocol
 * Byzantine Fault Tolerant consensus with safety and liveness guarantees
 *)

EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS 
    Nodes,          \* Set of all nodes in the system
    Values,         \* Set of possible values to agree upon
    Faulty,         \* Set of Byzantine faulty nodes
    MaxView,        \* Maximum view number
    MaxSeq          \* Maximum sequence number

ASSUME
    /\ Faulty \subseteq Nodes
    /\ Cardinality(Faulty) < Cardinality(Nodes) \div 3  \* Less than 1/3 Byzantine
    /\ MaxView \in Nat
    /\ MaxSeq \in Nat

VARIABLES
    view,           \* Current view number for each node
    phase,          \* Current phase: "idle", "preprepare", "prepare", "commit"
    prepared,       \* Set of prepared values for each node
    committed,      \* Set of committed values for each node
    messages,       \* Set of all messages sent
    decided,        \* Decision made by each node
    seqNum          \* Current sequence number

vars == <<view, phase, prepared, committed, messages, decided, seqNum>>

(* ----------------------------- Type Invariants ----------------------------- *)

TypeOK ==
    /\ view \in [Nodes -> 0..MaxView]
    /\ phase \in [Nodes -> {"idle", "preprepare", "prepare", "commit"}]
    /\ prepared \in [Nodes -> SUBSET (Values \X (0..MaxSeq))]
    /\ committed \in [Nodes -> SUBSET (Values \X (0..MaxSeq))]
    /\ messages \subseteq Message
    /\ decided \in [Nodes -> Values \cup {NULL}]
    /\ seqNum \in [Nodes -> 0..MaxSeq]

Message ==
    [type: {"preprepare"}, view: 0..MaxView, seq: 0..MaxSeq, 
     value: Values, sender: Nodes]
    \cup
    [type: {"prepare"}, view: 0..MaxView, seq: 0..MaxSeq,
     digest: Values, sender: Nodes]
    \cup
    [type: {"commit"}, view: 0..MaxView, seq: 0..MaxSeq,
     digest: Values, sender: Nodes]
    \cup
    [type: {"viewchange"}, newView: 0..MaxView, sender: Nodes,
     prepared: SUBSET (Values \X (0..MaxSeq))]

(* ----------------------------- Helper Functions ----------------------------- *)

IsPrimary(n, v) == 
    \* Node n is primary in view v
    (v % Cardinality(Nodes)) = (CHOOSE i \in 1..Cardinality(Nodes) : 
        (CHOOSE seq \in DOMAIN Nodes : seq[i] = n))

QuorumSize == (2 * Cardinality(Faulty)) + 1

CountMessages(msgType, v, s, val) ==
    Cardinality({m \in messages : 
        /\ m.type = msgType
        /\ m.view = v
        /\ m.seq = s
        /\ (msgType = "preprepare" => m.value = val)
        /\ (msgType \in {"prepare", "commit"} => m.digest = val)
    })

(* ----------------------------- Initial State ----------------------------- *)

Init ==
    /\ view = [n \in Nodes |-> 0]
    /\ phase = [n \in Nodes |-> "idle"]
    /\ prepared = [n \in Nodes |-> {}]
    /\ committed = [n \in Nodes |-> {}]
    /\ messages = {}
    /\ decided = [n \in Nodes |-> NULL]
    /\ seqNum = [n \in Nodes |-> 0]

(* ----------------------------- State Transitions ----------------------------- *)

\* Primary proposes a value
Propose(n, v, val) ==
    /\ n \notin Faulty
    /\ IsPrimary(n, view[n])
    /\ phase[n] = "idle"
    /\ seqNum[n] < MaxSeq
    /\ messages' = messages \cup {[
        type |-> "preprepare",
        view |-> view[n],
        seq |-> seqNum[n] + 1,
        value |-> val,
        sender |-> n
    ]}
    /\ seqNum' = [seqNum EXCEPT ![n] = seqNum[n] + 1]
    /\ phase' = [phase EXCEPT ![n] = "preprepare"]
    /\ UNCHANGED <<view, prepared, committed, decided>>

\* Node receives pre-prepare and moves to prepare phase
ReceivePrePrepare(n, m) ==
    /\ n \notin Faulty
    /\ m \in messages
    /\ m.type = "preprepare"
    /\ m.view = view[n]
    /\ phase[n] = "idle"
    /\ ~(\E prep \in prepared[n] : prep[2] = m.seq)
    /\ messages' = messages \cup {[
        type |-> "prepare",
        view |-> m.view,
        seq |-> m.seq,
        digest |-> m.value,
        sender |-> n
    ]}
    /\ phase' = [phase EXCEPT ![n] = "prepare"]
    /\ UNCHANGED <<view, prepared, committed, decided, seqNum>>

\* Node receives enough prepares and moves to commit phase
EnterCommitPhase(n, v, s, val) ==
    /\ n \notin Faulty
    /\ phase[n] = "prepare"
    /\ CountMessages("prepare", v, s, val) >= QuorumSize
    /\ prepared' = [prepared EXCEPT ![n] = prepared[n] \cup {<<val, s>>}]
    /\ messages' = messages \cup {[
        type |-> "commit",
        view |-> v,
        seq |-> s,
        digest |-> val,
        sender |-> n
    ]}
    /\ phase' = [phase EXCEPT ![n] = "commit"]
    /\ UNCHANGED <<view, committed, decided, seqNum>>

\* Node receives enough commits and decides
Decide(n, v, s, val) ==
    /\ n \notin Faulty
    /\ CountMessages("commit", v, s, val) >= QuorumSize
    /\ committed' = [committed EXCEPT ![n] = committed[n] \cup {<<val, s>>}]
    /\ decided' = [decided EXCEPT ![n] = val]
    /\ phase' = [phase EXCEPT ![n] = "idle"]
    /\ UNCHANGED <<view, prepared, messages, seqNum>>

\* View change when primary fails
InitiateViewChange(n) ==
    /\ n \notin Faulty
    /\ view[n] < MaxView
    /\ \/ phase[n] = "idle"  \* Timeout waiting for proposal
       \/ phase[n] \in {"prepare", "commit"}  \* Timeout in phase
    /\ view' = [view EXCEPT ![n] = view[n] + 1]
    /\ messages' = messages \cup {[
        type |-> "viewchange",
        newView |-> view[n] + 1,
        sender |-> n,
        prepared |-> prepared[n]
    ]}
    /\ phase' = [phase EXCEPT ![n] = "idle"]
    /\ UNCHANGED <<prepared, committed, decided, seqNum>>

\* Byzantine node sends arbitrary message
ByzantineAction(n) ==
    /\ n \in Faulty
    /\ \E m \in Message : messages' = messages \cup {m}
    /\ UNCHANGED <<view, phase, prepared, committed, decided, seqNum>>

(* ----------------------------- Next State ----------------------------- *)

Next ==
    \/ \E n \in Nodes, v \in Values : Propose(n, v, v)
    \/ \E n \in Nodes, m \in messages : ReceivePrePrepare(n, m)
    \/ \E n \in Nodes, v \in 0..MaxView, s \in 0..MaxSeq, val \in Values :
        EnterCommitPhase(n, v, s, val)
    \/ \E n \in Nodes, v \in 0..MaxView, s \in 0..MaxSeq, val \in Values :
        Decide(n, v, s, val)
    \/ \E n \in Nodes : InitiateViewChange(n)
    \/ \E n \in Faulty : ByzantineAction(n)

(* ----------------------------- Safety Properties ----------------------------- *)

\* Agreement: No two correct nodes decide different values
Agreement ==
    \A n1, n2 \in Nodes \ Faulty :
        (decided[n1] # NULL /\ decided[n2] # NULL) => 
        decided[n1] = decided[n2]

\* Validity: If all correct nodes propose the same value, they decide that value
Validity ==
    (\E v \in Values : \A n \in Nodes \ Faulty : 
        (\E m \in messages : m.type = "preprepare" /\ m.sender = n /\ m.value = v)) =>
    (\A n \in Nodes \ Faulty : decided[n] = NULL \/ decided[n] = v)

\* No conflicting prepares in same view
NoDuplicatePrepares ==
    \A n \in Nodes \ Faulty, v \in 0..MaxView, s \in 0..MaxSeq :
        Cardinality({val \in Values : <<val, s>> \in prepared[n]}) <= 1

(* ----------------------------- Liveness Properties ----------------------------- *)

\* Eventually all correct nodes decide (under synchrony assumptions)
EventualDecision ==
    <>(
        \A n \in Nodes \ Faulty : decided[n] # NULL
    )

\* View changes eventually succeed
ViewChangeProgress ==
    \A n \in Nodes \ Faulty :
        [](phase[n] = "idle" ~> 
            \/ <>(decided[n] # NULL)
            \/ <>(view[n] > view[n]'))

(* ----------------------------- Temporal Properties ----------------------------- *)

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

THEOREM Spec => [](TypeOK /\ Agreement /\ NoDuplicatePrepares)

(* ----------------------------- Model Checking Bounds ----------------------------- *)

\* For model checking with TLC
MCNodes == {"n1", "n2", "n3", "n4"}
MCFaulty == {"n4"}
MCValues == {"v1", "v2"}
MCMaxView == 3
MCMaxSeq == 5

=============================================================================