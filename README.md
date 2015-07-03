# clueminer-cli

This project is dependent on the main repository of [Clueminer](https://github.com/deric/clueminer). In future dependencies should be downloaded from a repository, right you have to build Clueminer first, then CLI (dependencies will be loaded from Maven cache).

## Build

just compile JARs

    mvn install

and run it:

    ./run


  - to build single all containing JAR:
```
mvn assembly:assembly
```
