package com.softwaremill.events

import scala.reflect.ClassTag

/**
 * Maps event types to event listeners (synchronous and asynchronous) as well as model update functions.
 */
case class Registry(
    eventListeners: Map[Class[_], List[EventListener[_]]],
    asyncEventListeners: Map[Class[_], List[EventListener[_]]],
    modelUpdates: Map[Class[_], List[ModelUpdate[_]]],
    eventsByTypeWithModelUpdate: Map[String, Class[_]]
) {

  def registerEventListener[T: ClassTag](h: EventListener[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(eventListeners = eventListeners + (key -> (h :: eventListeners.getOrElse(key, Nil))))
  }

  def registerAsyncEventListener[T: ClassTag](h: EventListener[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(asyncEventListeners = asyncEventListeners + (key -> (h :: asyncEventListeners.getOrElse(key, Nil))))
  }

  def registerModelUpdate[T: ClassTag](h: ModelUpdate[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(
      modelUpdates = modelUpdates + (key -> (h :: modelUpdates.getOrElse(key, Nil))),
      eventsByTypeWithModelUpdate = addEventType(eventsByTypeWithModelUpdate, key)
    )
  }

  def eventClassIfHasModelUpdate(eventType: String) = eventsByTypeWithModelUpdate.get(eventType)
  def eventTypesWithModelUpdates = eventsByTypeWithModelUpdate.keySet
  private def addEventType(eventsByType: Map[String, Class[_]], cls: Class[_]) =
    eventsByType + (PartialEvent.eventTypeFromClass(cls) -> cls)

  private[events] def lookupEventListeners[T](e: Event[T]): List[EventListener[T]] =
    doLookup[T, EventListener](e.data.getClass, eventListeners)

  private[events] def hasAsyncEventListeners(e: Event[_]): Boolean =
    lookupAsyncEventListeners(e).nonEmpty

  private[events] def lookupAsyncEventListeners[T](e: Event[T]): List[EventListener[T]] =
    doLookup[T, EventListener](e.data.getClass, asyncEventListeners)

  private[events] def lookupModelUpdates[T](e: Event[T]): List[ModelUpdate[T]] =
    doLookup[T, ModelUpdate](e.data.getClass, modelUpdates)

  private def doLookup[T, W[_]](cls: Class[_], m: Map[Class[_], Any]): List[W[T]] = {
    val clss = supertypes(cls)
    // reverse so that listeners are returned in the order in which they were registered
    clss.flatMap(m.getOrElse(_, Nil).asInstanceOf[List[W[T]]].reverse)
  }

  private def supertypes(cls: Class[_]): List[Class[_]] = {
    val parents = Option(cls.getSuperclass).toList ++ cls.getInterfaces.toList
    cls :: parents.flatMap(supertypes)
  }
}

object Registry {
  def apply(): Registry = Registry(Map(), Map(), Map(), Map())
}
