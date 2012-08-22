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

## AnalyzeContainerInformation.java
A slow-running program that can be used to link component elements to the physical 
containers in which the items are stored.  In cases where finding aids reference
containers in ways that are not consistent with the way containers are identified
in the marc record, this program can summarize all the container names referenced 
by both records and a human can then create a mapping from one set of identifiers 
to the other.  Once that mapping is codified (and this program is updated to use 
it) the program can be run again and will add links between the records in the
repository.  Currently the program is hard-coded to work with the naming 
"conventions" in the "MSS5950" finding aid.

	mvn compile -Plink-containers -Dexec.args="(pid) summary"

	mvn compile -Plink-containers -Dexec.args="(pid) link"

## EADIndex.java 
Summarizes the recognized content in the repository while reindexing the 
individual objects.

Within solr.properties you can specify just a single "collection" to reindex
or you may specify that you wish to do a "dry run" which would list the pids
to be reindexed without actually sending any update messges to solr.

	mvn compile -Pindex



