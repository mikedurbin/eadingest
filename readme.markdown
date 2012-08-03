# eadingest

This simple set of java classes deals with the storage, retreival and indexing of
EAD XML files in fedora and solr.

## Setup

In src/main/resources update the properties files to point to appropriately
configured fedora repository and solr instances.

## EADIngest.java
A simple program that takes EAD xml files as input, fragments them
and stores them in fedora objects that are related in such a way as to allow
the EAD to be reconstructed from the pieces.

	mvn test -Pingest

## EADIndex.java 
Summarizes the recognized content in the repository while reindexing the 
individual objects.

	mvn test -Pindex



