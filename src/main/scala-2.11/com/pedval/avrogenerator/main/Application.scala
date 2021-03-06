package com.pedval.avrogenerator.main

import java.util.Properties

import com.pedval.avrogenerator.sink.KafkaSink
import kafka.serializer.StringDecoder
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import com.pedval.avrogenerator.avro.{AvroGenerator, PlayerAvro}
import com.pedval.avrogenerator.generatedclasses.Player
import com.pedval.avrogenerator.serializer.DatumSerializer

/**
  * Created by PJimen01 on 27/10/2016.
  */
object Application {

  def main(args: Array[String]) {

    if(args.length != 4) {
      println("----------------------")
      println("- ERROR              -")
      println("- Usage:             -")
      println("----------------------")

      System.exit(-1)
    }

    val conf = new SparkConf().setMaster("local[2]").setAppName(args(0))

    val ssc = new StreamingContext(conf, Seconds(args(1).toInt))

    val inputTopics = args(2).split(",").toSet

    val kafkaParams = Map[String, String]("metadata.broker.list" -> "localhost:9092")

    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, inputTopics)

    val kafkaSink = ssc.sparkContext.broadcast(KafkaSink[Array[Byte]](createProperties()))

    messages.foreachRDD(rdd => {
      rdd
        .map(PlayerAvro.generate)
        .map(player => new DatumSerializer[Player]
          .serialize(Player.SCHEMA$,player))
        .foreach(avro => kafkaSink.value.send(args(3), avro))

    })

    ssc.start()
    ssc.awaitTermination()

  }


  def createProperties():  Properties = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("serializer.class", "kafka.serializer.StringEncoder")
    props.put("producer.type", "async")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer")

    props
  }

}
