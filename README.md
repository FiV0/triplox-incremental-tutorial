# {project}

Executing the program can either be done via
```sh
clj -M -m main :arg1 :arg2
```
or by compiling a jar via
```sh
clj -T:build clean
clj -T:build jar
```
and executing it via
```sh
java -jar target/lib-0.1.4.jar :arg1 :arg2
```

### Testing

```sh
clj -X:test
```

## License
