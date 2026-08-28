# Triplox incremental query tutorial

This repository is a little incremental query tutorial for [Triplox](https://github.com/FiV0/triplox), an Datomic-like
triplestore on top of [SlateDB](https://github.com/slatedb/slatedb).

Please check that the version below matches the [newest release](https://github.com/FiV0/triplox/pkgs/container/triplox) of Triplox and replace it accordingly.

You first need a running docker image of Triplox
```bash
docker pull ghcr.io/fiv0/triplox:0.1.0-alpha.7
docker run -p 5490:5490 ghcr.io/fiv0/triplox:0.1.0-alpha.7
```

In case you want a persistent node, start the image with
```bash
docker run -p 5490:5490 -e TRIPLOX_STORAGE=local -v triplox-data:/var/lib/triplox  ghcr.io/fiv0/triplox:0.1.0-alpha.7
```

### REPL
To get an nREPL you can connect to
```bash
clojure -M:dev:nrepl --port 7888
```

### Tutorial

[tutorial.clj](src/tutorial.clj) contains a REPL session you can follow along to get feel for how incremental queries
work in Triplox. We plan to add the tutorial for other client languages in the future.

### License

Apache License, Version 2.0
