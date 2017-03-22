package com.aziis98.test

import com.aziis98.kon.KonFormat
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Created by aziis98 on 22/03/2017.
 */
class KonFormatTest {

    @Test
    fun test() {

        val source = File("res/example-1.kon").readText()
        println("\n\n$source\n\n")

        val elements = KonFormat.parse(source)

        println(elements)

    }

}