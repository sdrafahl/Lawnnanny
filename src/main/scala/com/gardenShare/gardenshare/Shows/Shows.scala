package com.gardenShare.gardenshare

import cats.Show
import com.gardenShare.gardenshare.domain.Store.Address

package object Shows {
  implicit object AddressShow extends Show[Address] {
    def show(a: Address) = s"${a.street} ${a.city}, ${a.state.toString()} ${a.zipcode}"
  }
}
