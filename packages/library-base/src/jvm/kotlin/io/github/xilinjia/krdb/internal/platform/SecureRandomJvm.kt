/*
 * Copyright 2022 Realm Inc.
 * Copyright 2017-2019 Carlos Ballesteros Velasco and contributors
    * https://github.com/korlibs/korge/graphs/contributors
    * https://github.com/korlibs-archive/
 * All rights reserved.

 * MIT License

 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.

 * See: https://github.com/korlibs/korge/tree/main/krypto
*/

package io.github.xilinjia.krdb.internal.platform

import java.security.SecureRandom

private val jrandom = SecureRandom()

internal actual fun fillRandomBytes(array: ByteArray) {
    jrandom.nextBytes(array)
}
