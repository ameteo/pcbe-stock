This repository contains the implementation of Project 1 for the Concurrent and Event-Based Programming laboratory.

## **Team35** members:
- Stan Mirun Ameteo
- Sebo Diana Loredana
- Negrescu Bogdan


## Stock Market
Project specification translated from [here](http://labs.cs.upt.ro/labs/pcbe/html/proiecte/1/bursa.txt).

Implement/simulate a stock market where shares are being traded. The shares belong to the different companies listed on the stock market. Transactions are carried out by share buyers and sellers as follows:
- sellers offer for sale a number of shares for a certain price per share
- buyers make demands of buying a number of shares for a certain price per share
- when the price per share of an offer is the same as the price per share demanded by a buyer, a number of shares is traded, ```traded_shares = min (shares_sold, shares_demanded)```
- everyone has access to all the offers and demands as well as the entire history of transactions
- offers and demands can be modified at any time, as long as there is no transaction currently in progress
The implementation must contain a distributed system with one server and multiple clients, the clients being sellers and buyers.
The simulation will consist of a program where clients are started on separate threads. The clients are guided by an algorithm that takes into account all the common knowledge (offers, demands and the transaction history)