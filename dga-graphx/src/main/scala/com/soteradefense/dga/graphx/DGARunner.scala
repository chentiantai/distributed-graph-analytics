/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.soteradefense.dga.graphx

import com.soteradefense.dga.graphx.harness.Harness
import com.soteradefense.dga.graphx.hbse.HDFSHBSERunner
import com.soteradefense.dga.graphx.io.formats.EdgeInputFormat
import com.soteradefense.dga.graphx.kryo.DGAKryoRegistrator
import com.soteradefense.dga.graphx.lc.HDFSLCRunner
import com.soteradefense.dga.graphx.louvain.HDFSLouvainRunner
import com.soteradefense.dga.graphx.parser.CommandLineParser
import com.soteradefense.dga.graphx.pr.HDFSPRRunner
import com.soteradefense.dga.graphx.wcc.HDFSWCCRunner
import org.apache.spark.graphx.{GraphKryoRegistrator, Graph}
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.{SparkConf, SparkContext}

object DGARunner {
  def main(args: Array[String]) {
    val analytic = args(0)
    println(s"Analytic: $analytic")
    val newArgs = args.slice(1, args.length)
    val cmdLine = new CommandLineParser().parseCommandLine(newArgs)
    cmdLine.properties.foreach({ case (k, v) => System.setProperty(k, v)})
    val conf = new SparkConf().setMaster(cmdLine.master)
      .setAppName(cmdLine.appName)
      .setSparkHome(cmdLine.sparkHome)
      .setJars(cmdLine.jars.split(","))
    conf.setAll(cmdLine.customArguments)
    if(cmdLine.kryo){
      conf.set("spark.serializer", classOf[KryoSerializer].getCanonicalName)
      conf.set("spark.kryo.registrator", classOf[GraphKryoRegistrator].getCanonicalName)
    }
    val sc = new SparkContext(conf)
    val parallelism = Integer.parseInt(cmdLine.customArguments.getOrElse("parallelism", "-1"))
    val inputFormat = if (parallelism != -1) new EdgeInputFormat(cmdLine.input, cmdLine.edgeDelimiter, parallelism) else new EdgeInputFormat(cmdLine.input, cmdLine.edgeDelimiter)
    val edgeRDD = inputFormat.getEdgeRDD(sc)
    val graph = Graph.fromEdges(edgeRDD, None)
    var runner: Harness = null
    if (analytic.equals("wcc")) {
      runner = new HDFSWCCRunner(cmdLine.output, cmdLine.edgeDelimiter)
    }
    else if (analytic.equals("hbse")) {
      runner = new HDFSHBSERunner(cmdLine.output, cmdLine.edgeDelimiter)
    }
    else if (analytic.equals("louvain")) {
      val minProgress = cmdLine.customArguments.getOrElse("minProgress", "2000").toInt
      val progressCounter = cmdLine.customArguments.getOrElse("progressCounter", "1").toInt
      runner = new HDFSLouvainRunner(minProgress, progressCounter, cmdLine.output)
    }
    else if (analytic.equals("lc")) {
      runner = new HDFSLCRunner(cmdLine.output, cmdLine.edgeDelimiter)
    }
    else if (analytic.equals("pr")) {
      val delta = cmdLine.customArguments.getOrElse("delta", "0.001").toDouble
      runner = new HDFSPRRunner(cmdLine.output, cmdLine.edgeDelimiter, delta)
    }
    else {
      throw new IllegalArgumentException(s"$analytic is not supported")
    }
    runner.run(sc, graph)
  }
}
