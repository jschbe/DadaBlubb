package com.github.jschbe.dadablubb.data

import com.github.jschbe.dadablubb.core.Dataset
import com.github.jschbe.dadablubb.core.DatasetCodec
import com.github.jschbe.dadablubb.core.DatasetException
import com.github.jschbe.dadablubb.core.DatasetTextKey
import com.github.jschbe.dadablubb.core.datasetText
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Stream boundary shared by SAF and isolated tests. Call on an IO dispatcher.
 * Provider writes are not atomic; even close() can fail after a partial write. */
object DatasetDocuments {
    fun read(open: () -> InputStream?): Dataset =
        (open() ?: throw IOException("The document could not be opened.")).use(DatasetCodec::read)

    fun write(dataset: Dataset, open: () -> OutputStream?) {
        // encode validates the schema; check the complete size before truncating a target.
        val bytes = DatasetCodec.encode(dataset).toByteArray(Charsets.UTF_8)
        if (bytes.size > DatasetCodec.MAX_BYTES) {
            throw DatasetException(datasetText(DatasetTextKey.FORMATTED_SIZE))
        }
        (open() ?: throw IOException("The document could not be opened.")).use { it.write(bytes) }
    }
}
