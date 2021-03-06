#
# GRAKN.AI - THE KNOWLEDGE GRAPH
# Copyright (C) 2018 Grakn Labs Ltd
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

package(default_visibility = ["//visibility:public"])

load("//dependencies/maven:rules.bzl", "deploy_maven_jar")
load("@graknlabs_bazel_distribution//distribution:rules.bzl", "distribution_structure", "distribution_zip", "distribution_deb", "distribution_rpm")
load("@graknlabs_bazel_distribution//rpm/deployment:rules.bzl", "deploy_rpm")
load("@graknlabs_bazel_distribution//deb/deployment:rules.bzl", "deploy_deb")

exports_files(
    glob(["conf/**", "services/**"]),
    visibility = ["//visibility:public"]
)

java_library(
    name = "server",
    srcs = glob(["src/**/*.java"]),
    deps = [
        # Grakn Core dependencies
        "//common:common",
        "//graql/java:graql",
        "//protocol:protocol-java",

        # External dependencies
        "//dependencies/maven/artifacts/grakn/benchmark:lib",
        "//dependencies/maven/artifacts/com/datastax/cassandra:cassandra-driver-core", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/com/google/auto/value:auto-value",
        "//dependencies/maven/artifacts/com/google/code/findbugs:annotations",
        "//dependencies/maven/artifacts/com/google/code/findbugs:jsr305",
        "//dependencies/maven/artifacts/com/google/guava:guava",
        "//dependencies/maven/artifacts/commons-configuration:commons-configuration", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/commons-lang:commons-lang",
        "//dependencies/maven/artifacts/commons-cli:commons-cli", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/io/grpc:grpc-core",
        "//dependencies/maven/artifacts/io/grpc:grpc-stub",
        "//dependencies/maven/artifacts/io/zipkin/brave:brave",
        "//dependencies/maven/artifacts/org/antlr:antlr4-runtime", # sync version with @antlr4_runtime//jar
        "//dependencies/maven/artifacts/org/apache/cassandra:cassandra-all", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/cassandra:cassandra-thrift", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/commons:commons-lang3", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/hadoop:hadoop-annotations", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/hadoop:hadoop-common", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/hadoop:hadoop-mapreduce-client-core", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/spark:spark-core-2-11",
        "//dependencies/maven/artifacts/org/apache/spark:spark-launcher-2-11", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/thrift:libthrift", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/tinkerpop:gremlin-core", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/apache/tinkerpop:hadoop-gremlin",
        "//dependencies/maven/artifacts/org/apache/tinkerpop:spark-gremlin",
        "//dependencies/maven/artifacts/org/apache/tinkerpop:tinkergraph-gremlin",
        "//dependencies/maven/artifacts/org/rocksdb:rocksdbjni",
        "//dependencies/maven/artifacts/org/janusgraph:janusgraph-cassandra",
        "//dependencies/maven/artifacts/org/janusgraph:janusgraph-core",
        "//dependencies/maven/artifacts/org/janusgraph:janusgraph-hadoop",
        "//dependencies/maven/artifacts/org/scala-lang:scala-library", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/scala-lang:scalap", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/sharegov:mjson", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/slf4j:slf4j-api",
    ],
    runtime_deps = [
        "//dependencies/maven/artifacts/ch/qos/logback:logback-classic",
        "//dependencies/maven/artifacts/ch/qos/logback:logback-core",
        "//dependencies/maven/artifacts/io/grpc:grpc-netty",
        "//dependencies/maven/artifacts/io/netty:netty-all",
        "//dependencies/maven/artifacts/javax/servlet:javax-servlet-api", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/org/codehaus/janino:janino",
    ],
    resources = ["LICENSE"] + glob(["resources/*"]),
    resource_strip_prefix = "server",
    tags = ["maven_coordinates=grakn.core:server:{pom_version}"],
    javacopts = ["-XepDisableAllChecks"], # TODO: THIS NEEDS TO BE REMOVED

)

load("//dependencies/tools/checkstyle:checkstyle.bzl", "checkstyle_test")
checkstyle_test(
 name = "server-checkstyle",
 target = ":server",
 config = "//config/checkstyle:checkstyle.xml",
 suppressions = "//config/checkstyle:checkstyle-suppressions.xml",
 licenses = ["//config/checkstyle:licenses"],

)

java_binary(
    name = "server-binary",
    main_class = "grakn.core.daemon.GraknDaemon",
    runtime_deps = [
        "//daemon",
        "//server"
    ],
    visibility = ["//:__pkg__"],
)

deploy_maven_jar(
    name = "deploy-maven-jar",
    target = ":server",
    package = "server",
)

distribution_structure(
    name="grakn-core-server",
    targets = {
        "//server:server-binary": "server/services/lib/"
    },
    additional_files = {
        "//server:services/cassandra/cassandra.yaml": "server/services/cassandra/cassandra.yaml",
        "//server:services/cassandra/logback.xml": "server/services/cassandra/logback.xml",
        "//server:services/grakn/grakn-core-ascii.txt": "server/services/grakn/grakn-core-ascii.txt"
    },
    permissions = {
      "server/services/cassandra/cassandra.yaml": "0777",
    },
    visibility = ["//:__pkg__"]
)

distribution_zip(
    name = "distribution",
    distribution_structures = [":grakn-core-server", "//:grakn-core-bin"],
    empty_directories = [
        "server/db/cassandra",
        "server/db/queue"
    ],
    permissions = {
      "server/services/cassandra/cassandra.yaml": "0777",
      "server/db/cassandra": "0777",
      "server/db/queue": "0777",
    },
    output_filename = "grakn-core-server",
)

distribution_deb(
    name = "distribution-deb",
    package_name = "grakn-core-server",
    maintainer = "Grakn Labs <community@grakn.ai>",
    description = "Grakn Core (server)",
    version_file = "//:VERSION",
    depends = [
      "openjdk-8-jre",
      "grakn-core-bin"
    ],
    distribution_structures = [":grakn-core-server"],
    installation_dir = "/opt/grakn/core/",
    empty_dirs = [
        "opt/grakn/core/server/services/lib/",
        "var/lib/grakn/db/queue",
        "var/lib/grakn/db/cassandra"
     ],
    permissions = {
        "var/lib/grakn/db/queue": "0777",
        "var/lib/grakn/db/cassandra": "0777",
    },
    symlinks = {
        "opt/grakn/core/server/db/": "/var/lib/grakn/db/",
    },
)

deploy_deb(
    name = "deploy-deb",
    target = ":distribution-deb",
    deployment_properties = "//:deployment.properties",
)

distribution_rpm(
    name = "distribution-rpm",
    package_name = "grakn-core-server",
    installation_dir = "/opt/grakn/core/",
    version_file = "//:VERSION",
    spec_file = "//dependencies/distribution/rpm:grakn-core-server.spec",
    distribution_structures = [":grakn-core-server"],
    empty_dirs = [
        "opt/grakn/core/server/services/lib/",
        "var/lib/grakn/db/queue",
        "var/lib/grakn/db/cassandra"
     ],
    files = {
        "//server:services/cassandra/cassandra.yaml": "services/cassandra/cassandra.yaml",
        "//server:services/cassandra/logback.xml": "services/cassandra/logback.xml",
        "//server:services/grakn/grakn-core-ascii.txt": "services/grakn/grakn-core-ascii.txt"
    },
    permissions = {
        "services/cassandra/cassandra.yaml": "0777",
        "var/lib/grakn/db/queue": "0777",
        "var/lib/grakn/db/cassandra": "0777",
    },
    symlinks = {
        "opt/grakn/core/server/db/": "/var/lib/grakn/db/",
    },
)

deploy_rpm(
    name = "deploy-rpm",
    target = ":distribution-rpm",
    deployment_properties = "//:deployment.properties",
)
