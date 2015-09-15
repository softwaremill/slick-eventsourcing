package com.softwaremill.example.apikey

import com.softwaremill.events.ModelUpdate
import com.softwaremill.example.user.User._

class ApikeyListeners(apikeyModel: ApikeyModel) {
  val createdUpdated: ModelUpdate[ApikeyCreated] = e =>
    apikeyModel.updateNew(Apikey(e.aggregateId, e.userId, e.data.apikey, e.created))

  val deletedUpdated: ModelUpdate[ApikeyDeleted] = e =>
    apikeyModel.updateDelete(e.aggregateId)
}