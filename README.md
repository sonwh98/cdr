# CDR (Clojure Development REPL)

A Clojure/ClojureScript IDE completely in the browser. One of the difficulty in getting started with Clojure/LISP
programming is the tooling. The goal of CDR is to make Clojure development simple and easy for newbies and expert 
alike.


CDR integrates with git hosted on github or bitbucket.  CDR uses parainfer with CodeMirror for structural editing.
The only dependency is a web browser like Chrome and FireFox.

Long term goal is to turn this into a development environment for popular languages like 
Java/C#/Python/JavaScript as well.

# What works
* evaluate arbitary Clojure code in the browser. Open up browser developer's console to see output
* clone git repository from github or bitbucket via http

# Limitations

Requires bootstrap ClojureScript so cannot take advantage of advance optimization of Google Closure compiler

# Quick Start

Deploy using Docker:

```bash
% git clone https://github.com/sonwh98/cdr.git
% cd cdr
% docker build -t cdr .
% docker run -dp 3004:3004 --name cdr -t cdr
```

Once your docker container is running connect to http://localhost:3004/index.html

# Live Demo

http://cdr.stigmergy.systems
