# Fuse Build Yaml

This project is used to generate the YAML file that is consumed by [PiG](https://gitlab.cee.redhat.com/tcunning/piglet)
to generate the configuration and drive the PNC builds.

## Usage

This code base contain YAML files that are used to represent their configuration on PNC
build system. That means that, for example, the `base.yaml` file contains the configuration
for the [Red Hat Fuse - Base 7.5 CR1](http://orch.psi.redhat.com/pnc-web/#/build-groups/179)
build group on the PNC.

*Note*: beware that the version and milestones might be different at the time of reading.

The processing of the YAML files also take into consideration certain properties, which are
referenced in the format ${property.name} within the YAML. The source for these values may
be the `pom.xml` or the `pme.properties` on the root of this project.

*Note*: some content present on the `pom.xml` file *may* be processed and modified by the
CI.

Currently, the following files are present:

* base.yaml: for the base Fuse components used by all other projects
* core-sb1.yaml: for Fuse Core modules with Spring Boot v1
* common.yaml: for Fuse Common components and products
* dv.yaml: for the Data Virtualization project
* fusefis.yaml: for the legacy Fuse FIS project format (likely to be removed)
* ipaas.yaml: for iPaaS
* springboot2.yaml: for Fuse components built with Spring Boot v2 support
* quickstarts.yaml: Fuse QuickStarts

### Processing the YAML files

Although this project uss a single code base to store multiple YAML files, when it is
actually processed, it generates a single specific version for each of those.

Therefore, the first step when using this project to generate the YAML files is to set the
version for the specific file you want to generate:

```mvn -DprocessAllModules=true -DnewVersion=7.5.0.fuse-core-sb1-750015 -B org.codehaus.mojo:versions-maven-plugin:2.7:set```

Then, to generate the files:

```mvn -Dyaml.name=core-sb1 -Dbuild.stage=DR1 -Dbuild.milestone=DR1 clean package```

*Note*: the files can be deployed to a Maven repository server for wider use and that's what
our CI does.

Certain properties may be adjusted during build time to give more flexibility
to the output and ensure a correct mapping with the PNC configuration. The `build.stage` and
`build.milestone` are 2 examples of that.
