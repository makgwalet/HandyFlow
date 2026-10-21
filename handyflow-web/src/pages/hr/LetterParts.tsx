// Small components shared by the letter screens.
import { Chip } from "./AttendanceParts"
import { STATUS_LABEL, statusColors } from "./letters.logic"

export function LetterStatus({ status }: { status: string }) {
  const [color, bg] = statusColors(status)
  return <Chip text={STATUS_LABEL[status] ?? status} color={color} bg={bg} />
}
