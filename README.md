# CDR (Clojure Development REPL)

A Clojure/ClojureScript IDE completely in the browser. One of the difficulty in getting started with Clojure/LISP
programming is the tooling. The goal of CDR is to make Clojure development simple and easy for newbies and expert 
alike.


CDR integrates with git hosted on github or bitbucket.  CDR uses parainfer with CodeMirror for structural editing.
The only dependency is a web browser like Chrome and FireFox.

# What works
* evaluate arbitary Clojure code in the browser. Open up developer's console to see output
* clone git repository from github or bitbucket via http


# Quick Start

Deploy using Docker:

```bash
% git clone https://github.com/sonwh98/cdr.git
% cd cdr
% docker build -t cdr .
% docker run -p 3000:3000 cdr
```

Once your docker container is running connect to http://localhost:3000/index.html

# Demo

http://cdr.stigmergy.systems
