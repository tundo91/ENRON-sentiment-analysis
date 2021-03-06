package nl.vu.ai.lsde.enron

import java.io.InputStream
import java.sql.Timestamp

import org.apache.hadoop.fs.{Path, FileSystem}

object Commons {

    val SOURCE_ENRON_DATA = "hdfs:///user/hannesm/lsde/enron"

    val LSDE_USER_SPACE = "hdfs:///user/lsde03"
    val LSDE_ENRON = s"$LSDE_USER_SPACE/enron"
    val ENRON_EXTRACTED_TXT = s"$LSDE_ENRON/extracted_txt"

    // DFs & RDDs
    val ENRON_FULL_DATAFRAME = s"$LSDE_ENRON/full_df.parquet"
    val ENRON_SENTIMENT_DATAFRAME = s"$LSDE_ENRON/sentiment_df.parquet"
    val ENRON_SENTIMENT_RDD = s"$LSDE_ENRON/sentiment.rdd"
    val ENRON_SENTIMENT_RESUME_JSON = s"$LSDE_ENRON/sentiment_resume.json"
    val ENRON_SENTIMENT_RESUME_MB_JSON = s"$LSDE_ENRON/sentiment_resume_mb/sentiment_resume_%s.json"

    // PRICES
    val ENRON_STOCK_PRICES_CSV = s"$LSDE_ENRON/enron_stock_prices.csv"

    // CUSTODIANS LIST
    val ENRON_CUSTODIANS_CSV_RES = "/custodians_def.csv"
    val ENRON_CUSTODIANS_CSV_HDFS = s"$LSDE_ENRON/custodians_def.csv"

    // SPAM FILTER
    val ENRON_SPAM_DATA = s"$LSDE_ENRON/spam_dataset"
    val ENRON_SPAM_TF = s"$LSDE_ENRON/spam_tf"
    val ENRON_SPAM_IDF = s"$LSDE_ENRON/spam_idf"
    val ENRON_SPAM_MODEL = s"$LSDE_ENRON/spam_model"
    val ENRON_DATAFRAME_HAM = s"$LSDE_ENRON/ham_df.parquet"

    /**
      * Delete a folder from HDFS.
      *
      * @param path The folder to be removed
      */
    def deleteFolder(path: String): Unit = {
        try { hdfs.delete(new Path(path), true) }
        catch { case _ : Throwable => }
    }

    /**
      * Gets the HDFS filesystem
      *
      * @return The HDFS filesystem
      */
    def hdfs: FileSystem = {
        val hadoopConf = new org.apache.hadoop.conf.Configuration()
        FileSystem.get(new java.net.URI("hdfs:///"), hadoopConf)
    }

    /**
      * Gets the ENRON corpus v2 custodians from the local CSV resource.
      * The cluster seems to fail this loading, so use the CSV stored in the HDFS instead.
      *
      * @return The custodians
      */
    def getCustodians: Seq[Custodian] = {
        val stream : InputStream = getClass.getResourceAsStream(Commons.ENRON_CUSTODIANS_CSV_RES)
        val lines = scala.io.Source.fromInputStream(stream).getLines.toSeq

        lines.map { line =>
            val items = line.split(",")
            val role = items(2) match {
                case s: String if s.contains("N/A") => None
                case s: String => Some(s)
            }
            Custodian(items(0), items(1), role)
        }
    }

    // http://support.semantria.com/customer/portal/articles/1127703-calculating-the-average-sentiment-of-multiple-items
    def pnRatio(sentiments: Iterable[Double]): Double = {
        val postCount = sentiments.count( sentiment => if (sentiment > 0.0) true else false).toDouble
        val negCount = sentiments.count( sentiment => if (sentiment < 0.0) true else false).toDouble

        val ratio = postCount match {
            case x if postCount < negCount => -1*(negCount/postCount)
            case x if postCount > negCount => postCount/negCount
            case _ => 0.0
        }

        ratio match {
            case x if ratio < -0.0 => -1.0
            case x if ratio > 0.0 => 1.0
            case _ => 0.0
        }
    }
}

case class Custodian(dirName: String, completeName: String, role: Option[String]) {

    override def toString: String = s"$completeName ($dirName${if (role.isDefined) s", ${role.get}" else ""})"
}


case class Email(date: Timestamp, from: Seq[Custodian], to: Seq[Custodian], cc: Seq[Custodian],
                 bcc: Seq[Custodian], subject: String, body: String) {

    override def toString: String =
        s"Date: $date\n" +
        s"From: ${from.mkString(", ")}\n" +
        s"To: ${to.mkString(", ")}\n" +
        s"Cc: ${cc.mkString(", ")}\n" +
        s"Bcc: ${bcc.mkString(", ")}\n" +
        s"Subject: $subject\n\n$body\n"

}


case class MailBox(name: String, emails: Seq[Email])


case class EmailWithSentiment(date: Timestamp, from: Seq[Custodian], to: Seq[Custodian],
                              subject: String, sentiment: Double)

case class MailBoxWithSentiment(name: String, emails: Seq[EmailWithSentiment])
