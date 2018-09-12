#!/bin/bash

function release() {
  echo "Releasing project=$1 and version=$2"
  echo "======================================================================"
  git clone git@github.com:fabric8-quickstarts/$1.git	
  cd $1
  git remote add prod ssh://code.engineering.redhat.com/fabric8-quickstarts/$1.git
  git fetch prod
  git checkout -b $1-$2-redhat prod/$1-$2-redhat
  OLD_VERSION=`cat pom.xml | grep "<version>" | head -n 1 | sed 's/<version>\(.*\)<\/version>/\1/' | tr -d '[[:space:]]'`
  perl -p -i -e "s/${OLD_VERSION}/$2-redhat-1/" pom.xml
  cp ../fis_7_settings.xml configuration/settings.xml
  git commit -a -m "Update settings.xml and increment release version"
  # TODO update redhat-x version accordingly...
  git tag $1-$2-redhat-1
  git push origin $1-$2-redhat-1
  git push prod $1-$2-redhat-1
  cd ..
  # need to update this file in ipaas-quickstarts before running build in PNC
  #echo "$1=https://github.com/fabric8-quickstarts/$1.git|$1-$2-redhat-1" >> repolist.txt
}

function add_redhat_version() {
  find . -name "*.json" | xargs perl -p -i -e "s/$1/$1-redhat-1/"	
}

# ${version.fuse.prefix} values from ${build.url}
release karaf-camel-amq ${karaf-camel-amq.version}
release karaf-camel-log ${karaf-camel-log.version}
release karaf-camel-rest-sql ${karaf-camel-rest-sql.version}
release karaf-cxf-rest ${karaf-cxf-rest.version}
release spring-boot-camel ${spring-boot-camel.version}
release spring-boot-camel-amq ${spring-boot-camel-amq.version} 
release spring-boot-camel-config ${spring-boot-camel-config.version}
release spring-boot-camel-drools ${spring-boot-camel-drools.version}
release spring-boot-camel-infinispan ${spring-boot-camel-infinispan.version} 
release spring-boot-camel-rest-sql ${spring-boot-camel-rest-sql.version}
release spring-boot-camel-teiid ${spring-boot-camel-teiid.version} 
release spring-boot-camel-xml ${spring-boot-camel-xml.version}
release spring-boot-cxf-jaxrs ${spring-boot-cxf-jaxrs.version} 
release spring-boot-cxf-jaxws ${spring-boot-cxf-jaxws.version}

# first need to checkout report of wfce (http://orch.cloud.pnc.engineering.redhat.com/pnc-web/#/projects/68/build-configs/271/build-records/6051 so repour-9795068bab28cc6eda6be5ab940f6a381656e47a) and push as tag wildfly-camel-examples-5.1.0.fuse-000059-redhat-1 to jboss fork... like:
# git checkout repour-9795068bab28cc6eda6be5ab940f6a381656e47a
# git tag wildfly-camel-examples-${version.wildfly.camel.examples}-redhat-1
# git push jboss wildfly-camel-examples-${version.wildfly.camel.examples}-redhat-1

git clone git@github.com:jboss-fuse/application-templates.git
cd application-templates
git fetch --tags
git checkout -b application-templates-${version.application.templates}-redhat application-templates-${version.application.templates}
git cherry-pick 58da744
add_redhat_version ${version.wildfly.camel.examples}

add_redhat_version ${karaf-camel-amq.version} 
add_redhat_version ${karaf-camel-log.version}
add_redhat_version ${karaf-camel-rest-sql.version} 
add_redhat_version ${karaf-cxf-rest.version}
add_redhat_version ${spring-boot-camel.version} 
add_redhat_version ${spring-boot-camel-amq.version} 
add_redhat_version ${spring-boot-camel-config.version}
add_redhat_version ${spring-boot-camel-drools.version}
add_redhat_version ${spring-boot-camel-infinispan.version}
add_redhat_version ${spring-boot-camel-rest-sql.version}
add_redhat_version ${spring-boot-camel-teiid.version}
add_redhat_version ${spring-boot-camel-xml.version}
add_redhat_version ${spring-boot-cxf-jaxrs.version}
add_redhat_version ${spring-boot-cxf-jaxws.version}
git commit -a -m "Update to redhat verisons"
git tag application-templates-${version.application.templates}-redhat-1
git push origin application-templates-${version.application.templates}-redhat-1



