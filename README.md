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
meta-search on a dataset:
```
./run -mts -d ~/_bench/real-world/iris.arff -e 'Ratkowsky-Lance,PointBiserial-Norm,NMI-sqrt'
```

  - to build single all containing JAR:
```
mvn assembly:assembly
```

mo clust:
```
./run -d ~/_bench/artificial/compound.arff -t arff -e "AIC,NMI-sqrt" -a Chameleon --cluster rows -exp Ch2-mo-ICS -p "{cutoff-strategy:External_cutoff,merger:MOM-HS,mo_objective_1:ICS,mo_objective_2:CLS,mo_sort:Shatovska,debug:2}"
```
