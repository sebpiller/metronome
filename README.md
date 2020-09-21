# TicTac

Tic-Tac is a simple API that tries to dispatch events to a lambda at the most accurate rythm it possibly can.

You configure the rate you want to be notified by events, and you will get them with a bellow-millisecond accuracy. 

It automatically compensates the time of execution taken by the lambda to process. 
 
Performance note: on my machine, begin to drop events at about 160bpm with 300ms of lambda execution.
