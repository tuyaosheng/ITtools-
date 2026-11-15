/** 各小组的标识色（手账风的柔和色），组号循环取色。 */
const COLORS = ['#e76f51', '#2a9d8f', '#e9a23b', '#457b9d', '#9b5de5', '#43aa8b', '#f15bb5', '#6d8b3c', '#d17a22', '#3a86ff']

export const groupColor = (groupNo: number) => COLORS[(Math.max(1, groupNo) - 1) % COLORS.length]
