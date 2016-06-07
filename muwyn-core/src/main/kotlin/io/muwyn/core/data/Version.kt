/*
 * Copyright 2016 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.muwyn.core.data

data class Version(val id: String = INITIAL_ID, val sequence: Long = INITIAL_SEQUENCE) {
    companion object {
        val INITIAL_ID = "-1"
        val INITIAL_SEQUENCE = -1L
    }
}

fun Version.increment(newId: String): Version = this.copy(id = newId, sequence = this.sequence + 1L)