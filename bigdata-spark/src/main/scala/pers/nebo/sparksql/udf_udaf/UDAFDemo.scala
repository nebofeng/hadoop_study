package pers.nebo.sparksql.udf_udaf

import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 思考：
  * 想计算工资的平均值。
  * 首先计算出所有人的工资的和
  * 然后再计算出有多少人
  * 工资的和/人数=平均工资
  */
object UDAFDemo  extends UserDefinedAggregateFunction{

  /**
    * 定义缓存字段的名称和数据类型
    */
  def bufferSchema:StructType = StructType(
    /*

    ::(构造列表)

       用法为x::list,其中x为加入到头部的元素，无论x是列表与否，它都只将成为新生成列表的第一个元素，
       也就是说新生成的列表长度为list的长度＋1 x::list等价于list.::(x)

        scala> "A"::"B"::Nil
        res0: List[String] = List(A, B)

       参考链接：https://www.jianshu.com/p/16bc484d0f37
     */
    StructField("total",DoubleType,true)::StructField("count",IntegerType,true)::Nil
  )
  /**
    * 定义输出的数据类型
    */
  def dataType: DataType = DoubleType
  /**
    * 是否指定唯一
    */
  def deterministic: Boolean = true
  /**
    * 最后的目标就是做如下的计算
    */
  def evaluate(buffer: Row): Any = {
    val  total= buffer.getDouble(0)
    val count=buffer.getInt(1);
    total/count
  }
  /**
    * 对于参数计算的值进行初始化
    *  两个部分的初始化，
    *  1. 在 map、端每个rdd分区内， 在rdd 每个分区内，按照group by 的字段分组，每个分组都有一个初始化的值
    *  2.  在reduce 每个初始化
    */
  def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer.update(0, 0.0)
    buffer.update(1, 0)
  }
  /**
    * 定义输入的数据类型
    */
  def inputSchema: StructType = StructType(
    StructField("salary",DoubleType,true)::Nil
  )
  /**
    * 进行全局的统计
    * 最后merge时，在各个节点上的聚合值，要进行merge ，也就是聚合
    */
  def merge(buffer1: MutableAggregationBuffer,buffer2:Row): Unit = {
    val total1= buffer1.getDouble(0);
    val count1=buffer1.getInt(1);
    val total2= buffer2.getDouble(0);
    val count2=buffer2.getInt(1);
    buffer1.update(0, total1+total2)
    buffer1.update(1, count1+count2)
  }
  /**
    * 修改中间状态的值
    * 每个组，有新的值进来的时候，进行分组对应的聚合值计算
    */
  def update(buffer: MutableAggregationBuffer,input:Row): Unit ={
    val total= buffer.getDouble(0);
    val count=buffer.getInt(1);
    val currentsalary=input.getDouble(0) //salary
    buffer.update(0, total+currentsalary)
    buffer.update(1, count+1)
  }

  def main(args: Array[String]): Unit = {
    val conf=new SparkConf().setAppName("UDAFDemo")
    val sc=new SparkContext(conf);


    val hiveContext=new HiveContext(sc);
    hiveContext.udf.register("salary_avg",UDAFDemo)
    hiveContext.sql("select salary_avg(salary) from worker").show()

    /**
      * 2.2.x
      */
    val spark=SparkSession.builder().config(conf).getOrCreate()
    spark.udf.register("salary_avg",UDAFDemo)
  }
}
