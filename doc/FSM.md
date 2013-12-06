# Finite State Machine

A finite state machine (FSM) is an abstraction coupling data and behavior in a
way that prevents error states from being entered. A FSM can be a simple and 
elegant way to describe the logic of simple business processes, such as the 
lifecycle of a credit card charge, or the dispatch of drivers in a transport
system.

This particular module exports functions for dealing with extended state machines;
machines with more context/state than simply the current state the machine is in.
This allows for more powerful and concise FSMs, (in effect, making them infinite
state machines) at the cost of some complexity. It is recommended that as little
additional state be stored with the machine as possible.

These extended finite state machines conform mostly to the UML spec[^1] for
extended finite state machines, with two important differences. 

1) Transitions themselves do not have actions associated with them. 
2) Guards are not explicit; their functionality is expected to be
   incorporated in the actions.

Structurally, a finite state machine is a state. Since a state can have many 
states (called substates), this description is recursive. For more information,
see the reticulum.validation namespace. Examples FSMs may be found in the test namespace.


## States
   
A state is a map which contains a :name :: String key. It may also have a list of 
states under :states. If it does, it must specify an initial state, by name,
under the :initial key. It may also have an :actions :: Map key. Names of
states shall be unique across the entire FSM.

The :actions map may have one to three of any of the keys :event, :exit, :enter.
Those keys are lists of actions, described below. Event actions trigger when an
event is sent to the FSM, and bubble up to the superstate until either a 
transition occurs, or the remaining actions are short-circuited. Exit actions 
trigger when a state is departed, and enter actions are triggered when a state is
entered. 


## Actions

An action takes a context {current-state-name ...} and a callback
function. This means that  actions can be asynchronously executing, or running
in another thread or even another machine entirely; when the action is done,
it is expected to call the callback with a boolean indicating if the action
sequence currently being executed should continue or not, and the new context.

If the boolean is false, all other action execution ceases (unless a transition
would occur, in which case exit and enter actions proceed until completion or 
an exit or enter action short-circuits).

 
[1]: http://en.wikipedia.org/wiki/UML_state_machine
