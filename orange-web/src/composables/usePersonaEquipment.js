import { ElMessage } from 'element-plus'
import { updatePersonaAccessoryBlueprint, updatePersonaEquipment } from '../api/personaWorkshop'

export const usePersonaEquipment = ({ snapshot, reload }) => {
  const equipmentFor = dimension => snapshot.equipment.find(item => item.dimension === dimension)
  const saveEquipment = async item => {
    try {
      await updatePersonaEquipment(item.slotCode, {
        itemCode: item.itemCode, variantCode: item.variantCode, colorHex: item.colorHex,
        equipped: item.equipped, transform: item.transform,
      })
      ElMessage.success(item.equipped ? `${item.name}已穿戴` : `${item.name}已收起`)
    } catch (error) {
      await reload()
      throw error
    }
  }
  const setEquipmentVariant = async (item, variant) => { item.variantCode = variant; await saveEquipment(item) }
  const updateEquipmentTransform = (item, transform) => { item.transform = transform }
  const updateAccessoryBlueprint = (item, blueprint) => { item.blueprint = blueprint }
  const saveAccessoryBlueprint = async (item, blueprint) => {
    item.blueprint = blueprint
    try { await updatePersonaAccessoryBlueprint(item.slotCode, blueprint) }
    catch (error) { await reload(); throw error }
  }
  return { equipmentFor, saveEquipment, setEquipmentVariant, updateEquipmentTransform,
    updateAccessoryBlueprint, saveAccessoryBlueprint }
}
