# eadingest

This simple set of java classes deals with the storage, retreival and indexing of
EAD XML files in fedora and solr.

## Setup

In src/main/resources update the properties files to point to appropriately
configured fedora repository and solr instances.

## EADIngest.java
A simple program that takes EAD xml files as input, fragments them
and stores them in fedora objects that are related in such a way as to allow
the EAD to be reconstructed from the pieces.  This program also ingests
content model and other support objects.  Before running this program be sure 
to include the recognized XML files in the src/main/resources/ead directory.

	mvn compile -Pingest

At this time, only the following EAD files are supported:
- viu00003.xml
- viu01215.xml
- viu01265.xml
- viu02465.xml
- viuh00010.xml

You can also use this program to purge the support objects and the objects
created from EAD XML files.

	mvn compile -Ppurge

## EADIndex.java 
Summarizes the recognized content in the repository while reindexing the 
individual objects.

	mvn compile -Pindex



