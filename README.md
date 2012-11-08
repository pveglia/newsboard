# newsboard

A website written in noir which has an interface that is somehow
similar to popular news aggregator
[hacker news](http://news.ycombinator.com). It uses
[Redis](http://redis.io/) as a backend through
[carmine](https://github.com/ptaoussanis/carmine).

## Usage

If you use cake, substitute 'lein' with 'cake' below. Everything should work fine.

```bash
lein deps
lein run
```

## License

Copyright (C) 2012 Paolo Veglia

Distributed under the Eclipse Public License, the same as Clojure.

## Todo

* comments (started)

## DONE

* Authentication browserID?
* use jquery for vote ajax action
* make a latest list
* put form in the first page and redirect to the first page
* forbid multiple votes (via authentication and session)
* Refactoring (move methods in the right place MVC)
* add "time ago"
* delete items
* thread safeness (check redis transactions)
* Styling CSS
* just delete if i'm the owner of item
* improve style, put some padding on the left
