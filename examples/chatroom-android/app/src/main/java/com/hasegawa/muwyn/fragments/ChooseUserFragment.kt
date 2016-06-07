/*
 * Copyright 2016 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hasegawa.muwyn.fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.hasegawa.muwyn.R

class ChooseUserFragment : Fragment(), View.OnClickListener {

    interface UserListener {
        fun userChosen(id: String)
    }

    private var userListener: UserListener? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is UserListener) {
            userListener = context
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater!!.inflate(R.layout.fragment_who_are_you, container, false)

        val bt1 = root.findViewById(R.id.user1_bt) as Button
        val bt2 = root.findViewById(R.id.user2_bt) as Button

        bt1.setOnClickListener(this)
        bt2.setOnClickListener(this)

        return root
    }

    override fun onClick(v: View?) {
        if (v is Button) {
            userListener?.userChosen(v.text.toString())
        }
    }
}
