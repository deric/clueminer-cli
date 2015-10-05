# clueminer-cli

This project is dependent on the main repository of [Clueminer](https://github.com/deric/clueminer). In future dependencies should be downloaded from a repository, right you have to build Clueminer first, then CLI (dependencies will be loaded from Maven cache).

## Build

just compile JARs

    mvn install

and run it:

    ./run

changing available memory:

    JAVA_XMX=8192m ./run --algorithm "..." --data "/some/path"

run k-means clustering on a ARFF dataset (`k` will be used according to number of classes in the dataset ):
```
./run -d ~/_bench/artificial/aggregation.arff -t arff -a k-means -e "AIC,NMI-sqrt" --hint-k
```

  - to build single all containing JAR:
```
mvn assembly:assembly
```
