package com.tencent.angel.serving.servables.angel

import java.util
import java.io.File

import com.tencent.angel.core.graph.TensorProtos
import com.tencent.angel.core.saver.MetaGraphProtos.MetaGraphDef
import com.tencent.angel.ml.core.PredictResult
import com.tencent.angel.ml.core.conf.{MLConf, SharedConf}
import com.tencent.angel.ml.core.data.LabeledData
import com.tencent.angel.ml.core.local.{LocalEvnContext, LocalModel}
import com.tencent.angel.ml.core.utils.JsonUtils
import com.tencent.angel.serving.apis.prediction.ClassificationProtos.ClassificationResponse
import com.tencent.angel.serving.apis.prediction.InferenceProtos.MultiInferenceResponse
import com.tencent.angel.serving.apis.prediction.{ClassificationProtos, InferenceProtos, PredictProtos, RegressionProtos}
import com.tencent.angel.serving.apis.prediction.PredictProtos.PredictResponse
import com.tencent.angel.serving.apis.prediction.RegressionProtos.RegressionResponse
import com.tencent.angel.serving.core.StoragePath
import com.tencent.angel.serving.servables.common.SavedModelBundle
import org.slf4j.{Logger, LoggerFactory}

import com.tencent.angel.utils.ProtoUtils
import com.tencent.angel.serving.servables.Utils.predictResult2TensorProto

class AngelSavedModelBundle(model: LocalModel) extends SavedModelBundle {
  private val LOG = LoggerFactory.getLogger(classOf[AngelSavedModelBundle])

  override val session: Session = null
  override val metaGraphDef: MetaGraphDef = null

  override def runClassify(runOptions: RunOptions, request: ClassificationProtos.ClassificationRequest, responseBuilder: ClassificationResponse.Builder): Unit = ???

  override def runMultiInference(runOptions: RunOptions, request: InferenceProtos.MultiInferenceRequest, responseBuilder: MultiInferenceResponse.Builder): Unit = ???

  override def runPredict(runOptions: RunOptions, request: PredictProtos.PredictRequest, responseBuilder: PredictResponse.Builder): Unit = {
    val modelSpec = request.getModelSpec
    val iter = request.getInputsMap.entrySet().iterator()

    responseBuilder.setModelSpec(modelSpec)

    while(iter.hasNext) {
      val entry = iter.next()
      val key = entry.getKey
      val value = entry.getValue

      val res: PredictResult = model.predict(new LabeledData(ProtoUtils.toVector(value), 0.0))

      LOG.info(s"res: ${res.getText}")
      responseBuilder.putOutputs(key, predictResult2TensorProto(res))
    }
  }

  override def runRegress(runOptions: RunOptions, request: RegressionProtos.RegressionRequest, responseBuilder: RegressionResponse.Builder): Unit = {

  }
}

object AngelSavedModelBundle {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  def apply(path: StoragePath): SavedModelBundle = {
    // load
    val graphJsonFile = s"$path${File.separator}graph.json"
    LOG.info(s"the graph file is $graphJsonFile")

    assert(new File(graphJsonFile).exists())

    val conf = SharedConf.get()
    conf.set(MLConf.ML_JSON_CONF_FILE, graphJsonFile)
    conf.setJson()

    println(JsonUtils.J2Pretty(conf.getJson))

    LOG.info(s"model load path is $path ")

     // update model load path
     conf.set(MLConf.ML_LOAD_MODEL_PATH, path)

    val model = new LocalModel(conf)
    LOG.info(s"buildNetwork for model")
    model.buildNetwork()

    LOG.info(s"start to load parameters for model")
    model.loadModel(LocalEvnContext(), path)

    LOG.info(s"model has loaded!")

    new AngelSavedModelBundle(model)
  }
}
